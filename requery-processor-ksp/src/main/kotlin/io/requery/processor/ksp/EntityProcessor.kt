/*
 * Copyright 2023 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.processor.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.javapoet.*
import io.requery.*
import io.requery.meta.*
import io.requery.proxy.*
import io.requery.query.Order
import jakarta.annotation.Generated
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.*
import javax.lang.model.element.Modifier
import kotlin.reflect.KClass

/**
 * KSP processor for generating requery entities.
 * This is a modern alternative to the KAPT-based processor.
 */
class EntityProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val options: Map<String, String> = environment.options
    
    private val entityDescriptors = mutableMapOf<String, EntityDescriptor>()
    private val superclassDescriptors = mutableMapOf<String, EntityDescriptor>()
    private val embeddedDescriptors = mutableMapOf<String, EntityDescriptor>()
    private val processedEntities = mutableSetOf<String>()
    private val generatedModelPackages = mutableSetOf<String>()
    
    companion object {
        // Configuration options
        const val GENERATE_MODEL = "generate.model"
        const val GENERATE_ALWAYS = "generate.always"
        const val GENERATE_JPA = "generate.jpa"
        
        // Common annotations and classes
        private const val JAKARTA_ENTITY = "jakarta.persistence.Entity"
        private const val JAKARTA_MAPPED_SUPERCLASS = "jakarta.persistence.MappedSuperclass"
        private const val JAKARTA_EMBEDDABLE = "jakarta.persistence.Embeddable"
    }
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()
        
        try {
            // Process @Entity, @Superclass, and @Embedded annotations
            val entitySymbols = resolver.getSymbolsWithAnnotation(Entity::class.qualifiedName!!)
            val superclassSymbols = resolver.getSymbolsWithAnnotation(Superclass::class.qualifiedName!!)
            val embeddedSymbols = resolver.getSymbolsWithAnnotation(Embedded::class.qualifiedName!!)
            
            // Process JPA annotations if enabled
            val jpaEntitySymbols = if (getOption(GENERATE_JPA, true)) {
                resolver.getSymbolsWithAnnotation(JAKARTA_ENTITY)
            } else {
                emptySequence()
            }
            
            val jpaSuperclassSymbols = if (getOption(GENERATE_JPA, true)) {
                resolver.getSymbolsWithAnnotation(JAKARTA_MAPPED_SUPERCLASS)
            } else {
                emptySequence()
            }
            
            val jpaEmbeddedSymbols = if (getOption(GENERATE_JPA, true)) {
                resolver.getSymbolsWithAnnotation(JAKARTA_EMBEDDABLE)
            } else {
                emptySequence()
            }
            
            // Process superclasses first to build inheritance hierarchy
            val allSuperclassSymbols = (superclassSymbols + jpaSuperclassSymbols)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                
            processSuperclassDeclarations(allSuperclassSymbols).let { 
                invalidSymbols.addAll(it) 
            }
            
            // Process embeddables
            val allEmbeddedSymbols = (embeddedSymbols + jpaEmbeddedSymbols)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                
            processEmbeddableDeclarations(allEmbeddedSymbols).let { 
                invalidSymbols.addAll(it) 
            }
            
            // Process entity classes
            val allEntitySymbols = (entitySymbols + jpaEntitySymbols)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                
            processEntityDeclarations(allEntitySymbols, resolver).let { 
                invalidSymbols.addAll(it) 
            }
            
            // Build entity graph and process relationships
            val entityGraph = buildEntityGraph()
            
            // Validate the entity graph
            validateEntityGraph(entityGraph).let {
                invalidSymbols.addAll(it)
            }
            
            // Generate entity implementations and metadata
            val hasErrors = invalidSymbols.isNotEmpty()
            if (!hasErrors || getOption(GENERATE_ALWAYS, true)) {
                generateEntityImplementations(entityGraph)
            }
            
            // Generate model classes if requested
            if (getOption(GENERATE_MODEL, true)) {
                generateModelClasses(entityGraph)
            }
        } catch (e: Exception) {
            logger.error("Error during processing: ${e.message}")
            e.printStackTrace()
        }
        
        return invalidSymbols
    }
    
    private fun processSuperclassDeclarations(superclassDeclarations: Sequence<KSClassDeclaration>): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()
        
        superclassDeclarations.forEach { classDeclaration ->
            try {
                val descriptor = createEntityDescriptor(classDeclaration, isSuperclass = true)
                superclassDescriptors[descriptor.qualifiedName] = descriptor
            } catch (e: Exception) {
                logger.error("Error processing superclass: ${e.message}", classDeclaration)
                invalidSymbols.add(classDeclaration)
            }
        }
        
        return invalidSymbols
    }
    
    private fun processEmbeddableDeclarations(embeddableDeclarations: Sequence<KSClassDeclaration>): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()
        
        embeddableDeclarations.forEach { classDeclaration ->
            try {
                val descriptor = createEntityDescriptor(classDeclaration, isEmbedded = true)
                embeddedDescriptors[descriptor.qualifiedName] = descriptor
            } catch (e: Exception) {
                logger.error("Error processing embeddable: ${e.message}", classDeclaration)
                invalidSymbols.add(classDeclaration)
            }
        }
        
        return invalidSymbols
    }
    
    private fun processEntityDeclarations(
        entityDeclarations: Sequence<KSClassDeclaration>, 
        resolver: Resolver
    ): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()
        
        entityDeclarations.forEach { classDeclaration ->
            try {
                val descriptor = createEntityDescriptor(classDeclaration)
                
                // Process superclass inheritance
                processSuperclassInheritance(descriptor, classDeclaration, resolver)
                
                entityDescriptors[descriptor.qualifiedName] = descriptor
            } catch (e: Exception) {
                logger.error("Error processing entity: ${e.message}", classDeclaration)
                invalidSymbols.add(classDeclaration)
            }
        }
        
        return invalidSymbols
    }
    
    private fun processSuperclassInheritance(
        descriptor: EntityDescriptor,
        classDeclaration: KSClassDeclaration,
        resolver: Resolver
    ) {
        // Process superclass hierarchy
        var superType = classDeclaration.superTypes.firstOrNull()
        while (superType != null) {
            val superTypeDeclaration = superType.resolve().declaration
            if (superTypeDeclaration is KSClassDeclaration && 
                superTypeDeclaration.qualifiedName != null) {
                
                val superTypeName = superTypeDeclaration.qualifiedName!!.asString()
                val superDescriptor = superclassDescriptors[superTypeName]
                
                if (superDescriptor != null) {
                    // Merge properties from superclass
                    descriptor.properties.addAll(superDescriptor.properties)
                }
                
                // Continue up the hierarchy
                superType = superTypeDeclaration.superTypes.firstOrNull()
            } else {
                break
            }
        }
        
        // Process interfaces
        for (interfaceType in classDeclaration.superTypes) {
            val declaration = interfaceType.resolve().declaration
            if (declaration is KSClassDeclaration && 
                declaration.classKind == ClassKind.INTERFACE &&
                declaration.qualifiedName != null) {
                
                val interfaceName = declaration.qualifiedName!!.asString()
                val superDescriptor = superclassDescriptors[interfaceName]
                
                if (superDescriptor != null) {
                    // Merge properties from interface
                    descriptor.properties.addAll(superDescriptor.properties)
                }
            }
        }
    }
    
    private fun createEntityDescriptor(
        classDeclaration: KSClassDeclaration,
        isSuperclass: Boolean = false,
        isEmbedded: Boolean = false
    ): EntityDescriptor {
        val packageName = classDeclaration.packageName.asString()
        val simpleName = classDeclaration.simpleName.asString()
        val qualifiedName = classDeclaration.qualifiedName?.asString() 
            ?: error("Entity must have a qualified name")
        
        logger.info("Processing ${when {
            isSuperclass -> "superclass"
            isEmbedded -> "embeddable"
            else -> "entity"
        }}: $qualifiedName")
        
        // Determine class type
        val isInterface = classDeclaration.classKind == ClassKind.INTERFACE
        val isAbstract = classDeclaration.modifiers.contains(Modifier.ABSTRACT)
        
        // Get entity metadata from annotations
        val tableName = classDeclaration.getAnnotationsByType(Table::class).firstOrNull()?.name ?: simpleName
        
        // Check for PropertyNameStyle annotation
        val propertyNameStyle = classDeclaration.getAnnotationsByType(io.requery.PropertyNameStyle::class)
            .firstOrNull()?.value ?: io.requery.PropertyNameStyle.BEAN
            
        // Check for PropertyVisibility annotation
        val propertyVisibility = classDeclaration.getAnnotationsByType(io.requery.PropertyVisibility::class)
            .firstOrNull()?.value ?: io.requery.PropertyVisibility.PUBLIC
        
        // Determine if entity is immutable
        val immutableAnnotation = classDeclaration.annotations.find {
            val name = it.shortName.asString()
            name == "AutoValue" || name == "Value" || name == "Immutable" || name == "Data"
        }
        val isImmutable = immutableAnnotation != null
        
        // Collect properties and their metadata
        val propertyDescriptors = mutableListOf<PropertyDescriptor>()
        
        classDeclaration.getAllProperties().forEach { property ->
            createPropertyDescriptor(property, classDeclaration)?.let {
                propertyDescriptors.add(it) 
            }
        }
        
        // For interfaces, also process methods as potential properties
        if (isInterface) {
            classDeclaration.getAllFunctions()
                .filter { it.simpleName.asString().startsWith("get") || it.simpleName.asString().startsWith("is") }
                .filter { it.parameters.isEmpty() }
                .forEach { method ->
                    val propertyName = convertMethodNameToPropertyName(method.simpleName.asString())
                    if (propertyName.isNotEmpty() && 
                        propertyDescriptors.none { it.name == propertyName }) {
                        
                        createPropertyDescriptorFromMethod(method, propertyName)?.let {
                            propertyDescriptors.add(it)
                        }
                    }
                }
        }
        
        // Determine if class has a key property
        val keyProperties = propertyDescriptors.filter { it.isKey }
        if (keyProperties.isEmpty() && !isSuperclass && !isEmbedded) {
            logger.error("Entity $qualifiedName must have at least one @Key property", classDeclaration)
        }
        
        return EntityDescriptor(
            packageName = packageName,
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            tableName = tableName,
            declaration = classDeclaration,
            isAbstract = isAbstract,
            isInterface = isInterface,
            isImmutable = isImmutable,
            isSuperclass = isSuperclass,
            isEmbedded = isEmbedded,
            propertyNameStyle = propertyNameStyle,
            propertyVisibility = propertyVisibility,
            properties = propertyDescriptors
        )
    }
    
    private fun createPropertyDescriptor(
        property: KSPropertyDeclaration, 
        classDeclaration: KSClassDeclaration
    ): PropertyDescriptor? {
        val propertyName = property.simpleName.asString()
        val returnType = property.type.resolve()
        
        // Skip if property has private visibility
        if (property.modifiers.contains(Modifier.PRIVATE)) {
            return null
        }
        
        // Determine property metadata from annotations
        val column = property.getAnnotationsByType(Column::class).firstOrNull()
        val isTransient = property.hasAnnotation(Transient::class)
        val isKey = property.hasAnnotation(Key::class) || 
                    property.hasAnnotation("jakarta.persistence.Id")
        val isGenerated = property.hasAnnotation(Generated::class) ||
                          property.hasAnnotation("jakarta.persistence.GeneratedValue")
        val isVersion = property.hasAnnotation(Version::class) ||
                        property.hasAnnotation("jakarta.persistence.Version")
        val isNullable = property.hasAnnotation(Nullable::class) ||
                        property.hasAnnotation("jakarta.annotation.Nullable") ||
                        returnType.isMarkedNullable
        val isLazy = property.hasAnnotation(Lazy::class)
        val isReadOnly = property.hasAnnotation(ReadOnly::class)
        
        // Determine relationship annotations
        val oneToOne = property.getAnnotationsByType(OneToOne::class).firstOrNull()
        val oneToMany = property.getAnnotationsByType(OneToMany::class).firstOrNull()
        val manyToOne = property.getAnnotationsByType(ManyToOne::class).firstOrNull()
        val manyToMany = property.getAnnotationsByType(ManyToMany::class).firstOrNull()
        
        // Get cardinality from relationship
        val cardinality = when {
            oneToOne != null -> Cardinality.ONE_TO_ONE
            oneToMany != null -> Cardinality.ONE_TO_MANY
            manyToOne != null -> Cardinality.MANY_TO_ONE
            manyToMany != null -> Cardinality.MANY_TO_MANY
            else -> null
        }
        
        // Determine collection type if applicable
        val isCollectionType = isCollectionType(returnType)
        
        // Create property descriptor
        return PropertyDescriptor(
            name = propertyName,
            columnName = column?.name?.takeIf { it.isNotEmpty() } ?: propertyName,
            typeName = returnType.declaration.qualifiedName?.asString() ?: "java.lang.Object",
            declaration = property,
            isKey = isKey,
            isGenerated = isGenerated,
            isVersion = isVersion,
            isNullable = isNullable,
            isTransient = isTransient,
            isLazy = isLazy,
            isReadOnly = isReadOnly,
            isCollection = isCollectionType,
            cardinality = cardinality
        )
    }
    
    private fun createPropertyDescriptorFromMethod(
        method: KSFunctionDeclaration,
        propertyName: String
    ): PropertyDescriptor? {
        val returnType = method.returnType?.resolve() ?: return null
        
        // Determine property metadata from annotations
        val column = method.getAnnotationsByType(Column::class).firstOrNull()
        val isTransient = method.hasAnnotation(Transient::class)
        val isKey = method.hasAnnotation(Key::class) || 
                    method.hasAnnotation("jakarta.persistence.Id")
        val isGenerated = method.hasAnnotation(Generated::class) ||
                          method.hasAnnotation("jakarta.persistence.GeneratedValue")
        val isVersion = method.hasAnnotation(Version::class) ||
                        method.hasAnnotation("jakarta.persistence.Version")
        val isNullable = method.hasAnnotation(Nullable::class) ||
                        method.hasAnnotation("jakarta.annotation.Nullable") ||
                        returnType.isMarkedNullable
        val isLazy = method.hasAnnotation(Lazy::class)
        val isReadOnly = method.hasAnnotation(ReadOnly::class)
        
        // Determine relationship annotations
        val oneToOne = method.getAnnotationsByType(OneToOne::class).firstOrNull()
        val oneToMany = method.getAnnotationsByType(OneToMany::class).firstOrNull()
        val manyToOne = method.getAnnotationsByType(ManyToOne::class).firstOrNull()
        val manyToMany = method.getAnnotationsByType(ManyToMany::class).firstOrNull()
        
        // Get cardinality from relationship
        val cardinality = when {
            oneToOne != null -> Cardinality.ONE_TO_ONE
            oneToMany != null -> Cardinality.ONE_TO_MANY
            manyToOne != null -> Cardinality.MANY_TO_ONE
            manyToMany != null -> Cardinality.MANY_TO_MANY
            else -> null
        }
        
        // Determine collection type if applicable
        val isCollectionType = isCollectionType(returnType)
        
        return PropertyDescriptor(
            name = propertyName,
            columnName = column?.name?.takeIf { it.isNotEmpty() } ?: propertyName,
            typeName = returnType.declaration.qualifiedName?.asString() ?: "java.lang.Object",
            declaration = method,
            isKey = isKey,
            isGenerated = isGenerated,
            isVersion = isVersion,
            isNullable = isNullable,
            isTransient = isTransient,
            isLazy = isLazy,
            isReadOnly = isReadOnly,
            isCollection = isCollectionType,
            cardinality = cardinality
        )
    }
    
    private fun buildEntityGraph(): EntityGraph {
        val graph = EntityGraph()
        
        // Add all entities to the graph
        entityDescriptors.values.forEach { descriptor ->
            graph.entities.add(descriptor)
            
            // Add entity properties
            descriptor.properties.forEach { property ->
                graph.properties.add(PropertyGraph(
                    owner = descriptor,
                    property = property
                ))
            }
        }
        
        // Process relationships between entities
        graph.properties.forEach { propertyGraph ->
            if (propertyGraph.property.cardinality != null) {
                // Try to find the referenced entity type
                val referencedTypeName = when {
                    propertyGraph.property.isCollection -> {
                        extractCollectionType(propertyGraph.property)
                    }
                    else -> {
                        propertyGraph.property.typeName
                    }
                }
                
                if (referencedTypeName != null) {
                    // Find the referenced entity
                    val referencedEntity = entityDescriptors[referencedTypeName] ?: 
                                          superclassDescriptors[referencedTypeName]
                    
                    if (referencedEntity != null) {
                        graph.relationships.add(RelationshipGraph(
                            sourceEntity = propertyGraph.owner,
                            targetEntity = referencedEntity,
                            property = propertyGraph.property
                        ))
                    }
                }
            }
        }
        
        return graph
    }
    
    private fun validateEntityGraph(graph: EntityGraph): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()
        
        // Validate entities
        graph.entities.forEach { entity ->
            // Every entity must have a key property
            if (entity.properties.none { it.isKey } && !entity.isSuperclass && !entity.isEmbedded) {
                logger.error("Entity ${entity.qualifiedName} must have at least one @Key property", entity.declaration)
                invalidSymbols.add(entity.declaration)
            }
            
            // Check for version property
            if (entity.properties.count { it.isVersion } > 1) {
                logger.error("Entity ${entity.qualifiedName} has multiple @Version properties", entity.declaration)
                invalidSymbols.add(entity.declaration)
            }
            
            // Check for key properties
            val keyProperties = entity.properties.filter { it.isKey }
            if (keyProperties.isEmpty() && !entity.isSuperclass && !entity.isEmbedded) {
                logger.error("Entity ${entity.qualifiedName} has no key properties", entity.declaration)
                invalidSymbols.add(entity.declaration)
            }
            
            // Validate property types
            entity.properties.forEach { property ->
                if (property.cardinality != null) {
                    // Validate relationship property
                    when (property.cardinality) {
                        Cardinality.ONE_TO_MANY, Cardinality.MANY_TO_MANY -> {
                            if (!property.isCollection) {
                                logger.error("Relationship property ${property.name} must be a collection type", 
                                    property.declaration)
                                invalidSymbols.add(property.declaration)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        
        // Validate relationships
        graph.relationships.forEach { relationship ->
            // Both ends of a relationship should exist
            val sourceEntity = relationship.sourceEntity
            val targetEntity = relationship.targetEntity
            
            if (sourceEntity == null || targetEntity == null) {
                logger.error("Invalid relationship: missing entity reference for ${relationship.property.name}", 
                    relationship.property.declaration)
                invalidSymbols.add(relationship.property.declaration)
            }
        }
        
        return invalidSymbols
    }
    
    private fun generateEntityImplementations(graph: EntityGraph) {
        // Generate entity implementations
        graph.entities.forEach { entity ->
            if (processedEntities.contains(entity.qualifiedName)) {
                return@forEach
            }
            
            try {
                if (!entity.isSuperclass) {
                    generateEntityImplementation(entity, graph)
                }
                
                // Always generate metadata model
                generateEntityMetaModel(entity, graph)
                
                processedEntities.add(entity.qualifiedName)
            } catch (e: Exception) {
                logger.error("Error generating entity implementation: ${e.message}, ${e.stackTraceToString()}")
            }
        }
    }
    
    private fun generateModelClasses(graph: EntityGraph) {
        try {
            // Group entities by package
            val packageToEntities = graph.entities.groupBy { it.packageName }
            
            // Generate model class for each package
            packageToEntities.forEach { (packageName, entities) ->
                if (!generatedModelPackages.contains(packageName)) {
                    generateModelClass(packageName, entities, graph)
                    generatedModelPackages.add(packageName)
                }
            }
        } catch (e: Exception) {
            logger.error("Error generating model classes: ${e.message}")
        }
    }
    
    private fun generateEntityImplementation(entity: EntityDescriptor, graph: EntityGraph) {
        if (entity.isImmutable) {
            // For immutable types, we only generate metadata
            return
        }
        
        val packageName = entity.packageName
        val className = "Generated${entity.simpleName}"
        
        val typeBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Generated::class.java)
        
        // Add implementations and interfaces
        if (entity.isInterface) {
            // For interfaces, implement the interface and Persistable
            typeBuilder.addSuperinterface(ClassName.get(entity.packageName, entity.simpleName))
            typeBuilder.addSuperinterface(ClassName.get(Persistable::class.java))
        } else if (entity.isAbstract) {
            // For abstract classes, extend the class and implement Persistable
            typeBuilder.superclass(ClassName.get(entity.packageName, entity.simpleName))
            typeBuilder.addSuperinterface(ClassName.get(Persistable::class.java))
        } else {
            // This shouldn't normally happen, but just in case
            logger.warn("Entity ${entity.qualifiedName} is neither an interface nor an abstract class")
            return
        }
        
        // Generate constructors
        generateConstructors(typeBuilder, entity)
        
        // Generate property state fields
        entity.properties.forEach { property ->
            if (!property.isTransient) {
                val propertyStateField = FieldSpec.builder(
                    ClassName.get(PropertyState::class.java),
                    getPropertyStateFieldName(property),
                    Modifier.PRIVATE
                ).build()
                
                typeBuilder.addField(propertyStateField)
            }
        }
        
        // Generate property fields for interfaces
        if (entity.isInterface) {
            entity.properties.forEach { property ->
                val typeName = getTypeName(property)
                val fieldName = property.name
                
                val fieldSpec = FieldSpec.builder(
                    typeName,
                    fieldName,
                    Modifier.PRIVATE
                ).build()
                
                typeBuilder.addField(fieldSpec)
            }
        }
        
        // Generate getters and setters
        entity.properties.forEach { property ->
            // Generate getter
            val getterMethodName = getGetterName(property)
            val returnType = getTypeName(property)
            
            val getterBuilder = MethodSpec.methodBuilder(getterMethodName)
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
            
            if (entity.isInterface) {
                getterBuilder.addStatement("return $L", property.name)
            } else {
                getterBuilder.addStatement("return super.$L()", getterMethodName)
            }
            
            typeBuilder.addMethod(getterBuilder.build())
            
            // Generate setter if not read-only
            if (!property.isReadOnly) {
                val setterMethodName = getSetterName(property)
                val paramName = property.name
                
                val setterBuilder = MethodSpec.methodBuilder(setterMethodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.VOID)
                    .addParameter(returnType, paramName)
                
                if (!property.isTransient) {
                    // Set property state for tracked properties
                    setterBuilder.addStatement("$L = $T.MODIFIED", 
                        getPropertyStateFieldName(property), PropertyState::class.java)
                }
                
                if (entity.isInterface) {
                    setterBuilder.addStatement("this.$L = $L", property.name, paramName)
                } else {
                    setterBuilder.addStatement("super.$L($L)", setterMethodName, paramName)
                }
                
                typeBuilder.addMethod(setterBuilder.build())
            }
        }
        
        // Generate EntityProxy methods
        generateProxyMethods(typeBuilder, entity)
        
        // Generate equals and hashCode
        generateEquals(typeBuilder, entity)
        generateHashCode(typeBuilder, entity)
        generateToString(typeBuilder, entity)
        
        // Write the generated class
        val javaFile = JavaFile.builder(packageName, typeBuilder.build())
            .build()
        
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = className
        )
        
        OutputStreamWriter(file, StandardCharsets.UTF_8).use { writer ->
            javaFile.writeTo(writer)
        }
    }
    
    private fun generateConstructors(typeBuilder: TypeSpec.Builder, entity: EntityDescriptor) {
        // Default constructor
        val defaultConstructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .build()
        
        typeBuilder.addMethod(defaultConstructor)
        
        // Constructor with all fields for interfaces
        if (entity.isInterface) {
            val constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
            
            entity.properties.forEach { property ->
                val paramName = property.name
                val paramType = getTypeName(property)
                
                constructorBuilder.addParameter(paramType, paramName)
                constructorBuilder.addStatement("this.$L = $L", paramName, paramName)
            }
            
            typeBuilder.addMethod(constructorBuilder.build())
        }
    }
    
    private fun generateProxyMethods(typeBuilder: TypeSpec.Builder, entity: EntityDescriptor) {
        // Add EntityProxy field
        val proxyType = ParameterizedTypeName.get(
            ClassName.get(EntityProxy::class.java),
            ClassName.get(entity.packageName, entity.simpleName)
        )
        
        typeBuilder.addField(
            FieldSpec.builder(proxyType, "${'$'}proxy", Modifier.PRIVATE, Modifier.TRANSIENT)
                .build()
        )
        
        // Implement Persistable methods
        typeBuilder.addMethod(
            MethodSpec.methodBuilder("getProxy")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(EntityProxy::class.java))
                .addStatement("return ${'$'}proxy")
                .build()
        )
        
        typeBuilder.addMethod(
            MethodSpec.methodBuilder("setProxy")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(EntityProxy::class.java), "proxy")
                .returns(TypeName.VOID)
                .addStatement("this.${'$'}proxy = ($T) proxy", proxyType)
                .build()
        )
        
        // getPropertyStates method
        val getPropertyStatesMethod = MethodSpec.methodBuilder("getPropertyStates")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(
                ParameterizedTypeName.get(
                    ClassName.get(Map::class.java),
                    ClassName.get(String::class.java),
                    ClassName.get(PropertyState::class.java)
                )
            )
            .addStatement("$T<String, PropertyState> states = new $T<>()", 
                Map::class.java, HashMap::class.java)
            
        entity.properties.forEach { property ->
            if (!property.isTransient) {
                getPropertyStatesMethod.addStatement("states.put($S, $L)", 
                    property.name, getPropertyStateFieldName(property))
            }
        }
        
        getPropertyStatesMethod.addStatement("return states")
        typeBuilder.addMethod(getPropertyStatesMethod.build())
    }
    
    private fun generateEquals(typeBuilder: TypeSpec.Builder, entity: EntityDescriptor) {
        val equals = MethodSpec.methodBuilder("equals")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(TypeName.OBJECT, "obj")
            .returns(TypeName.BOOLEAN)
            .addStatement("if (obj == this) return true")
            .addStatement("if (obj == null || obj.getClass() != getClass()) return false")
            .addStatement("$L other = ($L) obj", entity.simpleName, entity.simpleName)
            
        if (entity.properties.any { it.isKey }) {
            // Use key for equality if available
            entity.properties.filter { it.isKey }.forEach { keyProperty ->
                val getterName = getGetterName(keyProperty)
                val thisValue = if (entity.isInterface) keyProperty.name else "this.$getterName()"
                val otherValue = "other.$getterName()"
                
                if (isPrimitive(keyProperty.typeName)) {
                    equals.addStatement("if ($L != $L) return false", thisValue, otherValue)
                } else {
                    equals.addStatement("if ($L == null ? $L != null : !$L.equals($L)) return false", 
                        thisValue, otherValue, thisValue, otherValue)
                }
            }
        } else {
            // No key, compare all properties
            entity.properties.forEach { property ->
                val getterName = getGetterName(property)
                val thisValue = if (entity.isInterface) property.name else "this.$getterName()"
                val otherValue = "other.$getterName()"
                
                if (isPrimitive(property.typeName)) {
                    equals.addStatement("if ($L != $L) return false", thisValue, otherValue)
                } else {
                    equals.addStatement("if ($L == null ? $L != null : !$L.equals($L)) return false", 
                        thisValue, otherValue, thisValue, otherValue)
                }
            }
        }
        
        equals.addStatement("return true")
        
        typeBuilder.addMethod(equals.build())
    }
    
    private fun generateHashCode(typeBuilder: TypeSpec.Builder, entity: EntityDescriptor) {
        val hashCode = MethodSpec.methodBuilder("hashCode")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("int result = 31")
            
        if (entity.properties.any { it.isKey }) {
            // Use key for hashCode if available
            entity.properties.filter { it.isKey }.forEach { keyProperty ->
                val getterName = getGetterName(keyProperty)
                val value = if (entity.isInterface) keyProperty.name else "this.$getterName()"
                
                if (isPrimitive(keyProperty.typeName)) {
                    when (keyProperty.typeName) {
                        "boolean" -> hashCode.addStatement("result = 31 * result + ($L ? 1 : 0)", value)
                        "byte", "short", "int", "char" -> hashCode.addStatement("result = 31 * result + $L", value)
                        "long" -> hashCode.addStatement("result = 31 * result + (int)($L ^ ($L >>> 32))", value, value)
                        "float" -> hashCode.addStatement("result = 31 * result + Float.floatToIntBits($L)", value)
                        "double" -> {
                            hashCode.addStatement("long temp = Double.doubleToLongBits($L)", value)
                            hashCode.addStatement("result = 31 * result + (int)(temp ^ (temp >>> 32))")
                        }
                    }
                } else {
                    hashCode.addStatement("result = 31 * result + ($L != null ? $L.hashCode() : 0)", value, value)
                }
            }
        } else {
            // No key, use all properties
            entity.properties.forEach { property ->
                val getterName = getGetterName(property)
                val value = if (entity.isInterface) property.name else "this.$getterName()"
                
                if (isPrimitive(property.typeName)) {
                    when (property.typeName) {
                        "boolean" -> hashCode.addStatement("result = 31 * result + ($L ? 1 : 0)", value)
                        "byte", "short", "int", "char" -> hashCode.addStatement("result = 31 * result + $L", value)
                        "long" -> hashCode.addStatement("result = 31 * result + (int)($L ^ ($L >>> 32))", value, value)
                        "float" -> hashCode.addStatement("result = 31 * result + Float.floatToIntBits($L)", value)
                        "double" -> {
                            hashCode.addStatement("long temp = Double.doubleToLongBits($L)", value)
                            hashCode.addStatement("result = 31 * result + (int)(temp ^ (temp >>> 32))")
                        }
                    }
                } else {
                    hashCode.addStatement("result = 31 * result + ($L != null ? $L.hashCode() : 0)", value, value)
                }
            }
        }
        
        hashCode.addStatement("return result")
        
        typeBuilder.addMethod(hashCode.build())
    }
    
    private fun generateToString(typeBuilder: TypeSpec.Builder, entity: EntityDescriptor) {
        val toString = MethodSpec.methodBuilder("toString")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get(String::class.java))
            .addStatement("$T sb = new $T()", StringBuilder::class.java, StringBuilder::class.java)
            .addStatement("sb.append($S)", "${entity.simpleName}{")
            
        var first = true
        entity.properties.forEach { property ->
            val getterName = getGetterName(property)
            val value = if (entity.isInterface) property.name else "this.$getterName()"
            
            if (first) {
                toString.addStatement("sb.append($S).append($L)", "${property.name}=", value)
                first = false
            } else {
                toString.addStatement("sb.append($S).append($S).append($L)", ", ", "${property.name}=", value)
            }
        }
        
        toString.addStatement("sb.append($S)", "}")
            .addStatement("return sb.toString()")
            
        typeBuilder.addMethod(toString.build())
    }
    
    private fun generateEntityMetaModel(entity: EntityDescriptor, graph: EntityGraph) {
        val packageName = entity.packageName
        val className = "${entity.simpleName}_"
        
        val typeBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Generated::class.java)
        
        // Define a static instance field
        val instanceField = FieldSpec.builder(
            ClassName.get(packageName, className),
            "INSTANCE",
            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
        )
            .initializer("new $L()", className)
            .build()
        
        typeBuilder.addField(instanceField)
        
        // Add Type field with entity metadata
        val entityClassName = ClassName.get(entity.packageName, entity.simpleName)
        val typeParameterizedName = ParameterizedTypeName.get(
            ClassName.get(Type::class.java), entityClassName
        )
        
        val typeField = FieldSpec.builder(typeParameterizedName, "TYPE", Modifier.PUBLIC, Modifier.FINAL)
            .build()
        
        typeBuilder.addField(typeField)
        
        // Add attribute fields for each property
        for (property in entity.properties) {
            if (property.isTransient) continue
            
            // Determine attribute type
            val attributeType = getAttributeType(property)
            val attributeTypeName = ParameterizedTypeName.get(
                ClassName.get(attributeType), 
                entityClassName,
                boxIfPrimitive(getTypeName(property))
            )
            
            val attributeField = FieldSpec.builder(
                attributeTypeName,
                upperCaseUnderscoreRemovePrefixes(property.name),
                Modifier.PUBLIC, Modifier.FINAL
            ).build()
            
            typeBuilder.addField(attributeField)
        }
        
        // Add constructor to initialize fields
        val constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            
        // Initialize TYPE field
        val typeBuilderStatement = CodeBlock.builder()
            .add("this.TYPE = $T.builder($T.class)\n", TypeBuilder::class.java, entityClassName)
            .indent()
            .add(".setName($S)\n", entity.tableName)
            .add(".setClassType($T.class)\n", entityClassName)
            .add(".setReadOnly($L)\n", entity.properties.all { it.isReadOnly })
            .add(".setStateless($L)\n", false)
            .add(".setImmutable($L)\n", entity.isImmutable)
            
        // Add type properties
        typeBuilderStatement.add(".build()")
        
        constructorBuilder.addStatement(typeBuilderStatement.build())
        
        // Initialize attribute fields
        for (property in entity.properties) {
            if (property.isTransient) continue
            
            val attributeName = upperCaseUnderscoreRemovePrefixes(property.name)
            val typeName = boxIfPrimitive(getTypeName(property))
            val isKey = property.isKey
            val isVersion = property.isVersion
            val isNullable = property.isNullable
            val isReadOnly = property.isReadOnly
            val isGenerated = property.isGenerated
            
            val attributeBuilderStatement = CodeBlock.builder()
                .add("this.$L = ", attributeName)
                .add(getAttributeBuilderCode(property, entity, graph))
            
            constructorBuilder.addStatement(attributeBuilderStatement.build())
        }
        
        typeBuilder.addMethod(constructorBuilder.build())
        
        // Write the generated type
        val javaFile = JavaFile.builder(packageName, typeBuilder.build())
            .build()
        
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = className
        )
        
        OutputStreamWriter(file, StandardCharsets.UTF_8).use { writer ->
            javaFile.writeTo(writer)
        }
    }
    
    private fun generateModelClass(packageName: String, entities: List<EntityDescriptor>, graph: EntityGraph) {
        val className = "Models"
        
        val typeBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Generated::class.java)
        
        // Private constructor
        val constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addStatement("throw new $T()", UnsupportedOperationException::class.java)
            .build()
        
        typeBuilder.addMethod(constructor)
        
        // Add models field
        val entitiesSetType = ParameterizedTypeName.get(
            ClassName.get(Set::class.java),
            ParameterizedTypeName.get(
                ClassName.get(Type::class.java),
                WildcardTypeName.subtypeOf(TypeName.OBJECT)
            )
        )
        
        val modelsField = FieldSpec.builder(entitiesSetType, "MODELS", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .build()
        
        typeBuilder.addField(modelsField)
        
        // Static initializer to add all entity types
        val staticBlock = CodeBlock.builder()
            .addStatement("$T<$T<?>> set = new $T<>()", 
                Set::class.java, 
                ParameterizedTypeName.get(Type::class.java, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
                HashSet::class.java)
        
        // Add all entities to the set
        entities.forEach { entity ->
            staticBlock.addStatement("set.add($L.INSTANCE.TYPE)", "${entity.simpleName}_")
        }
        
        staticBlock.addStatement("MODELS = $T.unmodifiableSet(set)", Collections::class.java)
        
        typeBuilder.addInitializerBlock(staticBlock.build())
        
        // Write the generated type
        val javaFile = JavaFile.builder(packageName, typeBuilder.build())
            .build()
        
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = className
        )
        
        OutputStreamWriter(file, StandardCharsets.UTF_8).use { writer ->
            javaFile.writeTo(writer)
        }
    }
    
    // Helper methods
    
    private fun getAttributeType(property: PropertyDescriptor): Class<*> {
        return when {
            isStringType(property.typeName) -> StringAttribute::class.java
            isNumericType(property.typeName) -> NumericAttribute::class.java
            else -> Attribute::class.java
        }
    }
    
    private fun getAttributeBuilderCode(property: PropertyDescriptor, entity: EntityDescriptor, graph: EntityGraph): CodeBlock {
        val codeBuilder = CodeBlock.builder()
        
        val typeName = boxIfPrimitive(getTypeName(property))
        val getterName = getGetterName(property)
        val setterName = getSetterName(property)
        
        // Find the appropriate builder method
        val builderMethod = when {
            property.isCollection -> {
                when (extractCollectionTypeName(property.typeName)) {
                    "java.util.Set" -> "buildSet"
                    "java.util.List" -> "buildList"
                    "java.util.Collection" -> "buildCollection"
                    else -> "buildCollection"
                }
            }
            isMapType(property.typeName) -> "buildMap"
            else -> "build"
        }
        
        codeBuilder.add("$T.$L($T.class, $T.class, \"$L\")\n", 
            Attributes::class.java, 
            builderMethod, 
            ClassName.get(entity.packageName, entity.simpleName), 
            typeName, 
            property.name)
            .indent()
            .add(".setName(\"$L\")\n", property.columnName)
            .add(".setGetterName(\"$L\")\n", getterName)
            .add(".setSetterName(\"$L\")\n", setterName)
            .add(".setKey($L)\n", property.isKey)
            .add(".setVersion($L)\n", property.isVersion)
            .add(".setNullable($L)\n", property.isNullable)
            .add(".setGenerated($L)\n", property.isGenerated)
            .add(".setReadOnly($L)\n", property.isReadOnly)
            .add(".setLazy($L)\n", property.isLazy)
        
        // Add cardinality info if applicable
        if (property.cardinality != null) {
            codeBuilder.add(".setCardinality($T.$L)\n", 
                Cardinality::class.java, property.cardinality.name)
            
            // Add reference info
            val referencedTypeName = when {
                property.isCollection -> extractCollectionType(property)
                else -> property.typeName
            }
            
            if (referencedTypeName != null) {
                val referencedEntity = entityDescriptors[referencedTypeName] ?: 
                                      superclassDescriptors[referencedTypeName]
                
                if (referencedEntity != null) {
                    codeBuilder.add(".setReferencedType($L.INSTANCE.TYPE)\n", 
                        "${referencedEntity.simpleName}_")
                }
            }
        }
        
        // Add property state reference
        if (!entity.isImmutable) {
            codeBuilder.add(".setPropertyStateSupplier(entity -> (($T)entity).$L)\n", 
                ClassName.get(entity.packageName, "Generated${entity.simpleName}"), 
                getPropertyStateFieldName(property))
        }
        
        codeBuilder.add(".build()")
        
        return codeBuilder.build()
    }
    
    private fun getPropertyStateFieldName(property: PropertyDescriptor): String {
        return "${'$'}${property.name}_state"
    }
    
    private fun getGetterName(property: PropertyDescriptor): String {
        val name = property.name
        return if (property.typeName == "boolean") {
            "is${name.capitalize()}"
        } else {
            "get${name.capitalize()}"
        }
    }
    
    private fun getSetterName(property: PropertyDescriptor): String {
        val name = property.name
        return "set${name.capitalize()}"
    }
    
    private fun getTypeName(property: PropertyDescriptor): TypeName {
        return when (property.typeName) {
            "boolean" -> TypeName.BOOLEAN
            "byte" -> TypeName.BYTE
            "short" -> TypeName.SHORT
            "int" -> TypeName.INT
            "long" -> TypeName.LONG
            "float" -> TypeName.FLOAT
            "double" -> TypeName.DOUBLE
            "char" -> TypeName.CHAR
            "void" -> TypeName.VOID
            else -> {
                if (property.isCollection) {
                    val collectionType = extractCollectionTypeName(property.typeName)
                    val elementType = extractCollectionElementType(property.typeName)
                    
                    if (elementType != null) {
                        ParameterizedTypeName.get(
                            ClassName.bestGuess(collectionType),
                            ClassName.bestGuess(elementType)
                        )
                    } else {
                        ClassName.bestGuess(property.typeName)
                    }
                } else {
                    ClassName.bestGuess(property.typeName)
                }
            }
        }
    }
    
    private fun boxIfPrimitive(typeName: TypeName): TypeName {
        return typeName.box()
    }
    
    private fun isCollectionType(type: KSType): Boolean {
        val declaration = type.declaration
        val typeName = declaration.qualifiedName?.asString() ?: return false
        
        return typeName == "java.util.Collection" ||
               typeName == "java.util.List" ||
               typeName == "java.util.Set" ||
               typeName.startsWith("java.util.")
    }
    
    private fun isMapType(typeName: String): Boolean {
        return typeName == "java.util.Map" ||
               typeName.startsWith("java.util.Map<")
    }
    
    private fun isPrimitive(typeName: String): Boolean {
        return typeName == "boolean" ||
               typeName == "byte" ||
               typeName == "short" ||
               typeName == "int" ||
               typeName == "long" ||
               typeName == "float" ||
               typeName == "double" ||
               typeName == "char"
    }
    
    private fun isStringType(typeName: String): Boolean {
        return typeName == "java.lang.String" || typeName == "String"
    }
    
    private fun isNumericType(typeName: String): Boolean {
        return typeName == "byte" || typeName == "java.lang.Byte" ||
               typeName == "short" || typeName == "java.lang.Short" ||
               typeName == "int" || typeName == "java.lang.Integer" ||
               typeName == "long" || typeName == "java.lang.Long" ||
               typeName == "float" || typeName == "java.lang.Float" ||
               typeName == "double" || typeName == "java.lang.Double" ||
               typeName == "java.math.BigDecimal" || typeName == "java.math.BigInteger"
    }
    
    private fun extractCollectionType(property: PropertyDescriptor): String? {
        val typeName = property.typeName
        val regex = ".*<(.+)>".toRegex()
        val matchResult = regex.find(typeName)
        
        return matchResult?.groupValues?.get(1)?.trim()
    }
    
    private fun extractCollectionTypeName(typeName: String): String {
        val idx = typeName.indexOf('<')
        return if (idx > 0) typeName.substring(0, idx) else typeName
    }
    
    private fun extractCollectionElementType(typeName: String): String? {
        val regex = ".*<(.+)>".toRegex()
        val matchResult = regex.find(typeName)
        
        return matchResult?.groupValues?.get(1)?.trim()
    }
    
    private fun convertMethodNameToPropertyName(methodName: String): String {
        return if (methodName.startsWith("get") && methodName.length > 3) {
            methodName.substring(3, 4).lowercase() + methodName.substring(4)
        } else if (methodName.startsWith("is") && methodName.length > 2) {
            methodName.substring(2, 3).lowercase() + methodName.substring(3)
        } else {
            ""
        }
    }
    
    private fun upperCaseUnderscoreRemovePrefixes(name: String): String {
        val sb = StringBuilder()
        for (i in name.indices) {
            val c = name[i]
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_')
                }
                sb.append(c)
            } else {
                sb.append(c.uppercaseChar())
            }
        }
        return sb.toString()
    }
    
    private fun getOption(key: String, defaultValue: Boolean): Boolean {
        return options[key]?.toBoolean() ?: defaultValue
    }
    
    private fun KSAnnotated.hasAnnotation(annotationClass: KClass<*>): Boolean {
        return this.annotations.any { 
            it.shortName.asString() == annotationClass.simpleName || 
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationClass.qualifiedName
        }
    }
    
    private fun KSAnnotated.hasAnnotation(annotationName: String): Boolean {
        return this.annotations.any { 
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
        }
    }
}

/**
 * Descriptor for an entity class
 */
data class EntityDescriptor(
    val packageName: String,
    val simpleName: String,
    val qualifiedName: String,
    val tableName: String,
    val declaration: KSClassDeclaration,
    val isAbstract: Boolean = false,
    val isInterface: Boolean = false,
    val isImmutable: Boolean = false,
    val isSuperclass: Boolean = false,
    val isEmbedded: Boolean = false,
    val propertyNameStyle: PropertyNameStyle = PropertyNameStyle.BEAN,
    val propertyVisibility: PropertyVisibility = PropertyVisibility.PUBLIC,
    val properties: MutableList<PropertyDescriptor> = mutableListOf()
)

/**
 * Descriptor for an entity property
 */
data class PropertyDescriptor(
    val name: String,
    val columnName: String,
    val typeName: String,
    val declaration: KSDeclaration,
    val isKey: Boolean = false,
    val isGenerated: Boolean = false,
    val isVersion: Boolean = false,
    val isNullable: Boolean = false,
    val isTransient: Boolean = false,
    val isLazy: Boolean = false,
    val isReadOnly: Boolean = false,
    val isCollection: Boolean = false,
    val cardinality: Cardinality? = null
)

/**
 * Entity graph for representing the entity model and relationships
 */
class EntityGraph {
    val entities = mutableListOf<EntityDescriptor>()
    val properties = mutableListOf<PropertyGraph>()
    val relationships = mutableListOf<RelationshipGraph>()
}

/**
 * Graph node for a property
 */
data class PropertyGraph(
    val owner: EntityDescriptor,
    val property: PropertyDescriptor
)

/**
 * Graph node for a relationship between entities
 */
data class RelationshipGraph(
    val sourceEntity: EntityDescriptor,
    val targetEntity: EntityDescriptor,
    val property: PropertyDescriptor
)

/**
 * Provider for the KSP processor
 */
class EntityProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return EntityProcessor(environment)
    }
}