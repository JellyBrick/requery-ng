# Upgrade Notes for requery-ng

## Java 9+ Module System Support

Java 9+ Module System is now fully supported with proper `module-info.java` files for all modules:

- `io.requery`: Core module with SQL-related functionality 
- `io.requery.kotlin`: Kotlin extensions and DSL
- `io.requery.processor`: Annotation processor
- `io.requery.processor.ksp`: Kotlin Symbol Processing (KSP) processor
- `io.requery.jackson`: Jackson integration

These modules provide proper encapsulation and explicit dependencies, making the library more maintainable and allowing better interoperability with Java 9+ projects.

Benefits of module system integration:
- Strong encapsulation of internal implementation details
- Explicit declaration of dependencies between modules
- Improved security and reliability
- Better error detection at compile-time rather than runtime
- Improved maintainability with clearer boundaries

## Kotlin v2 Support

requery-ng now fully supports Kotlin 2.0 with enhanced language features and improved DSL.

### Enhanced Kotlin DSL

The Kotlin DSL has been extensively improved with Kotlin 2.0 features:

- More idiomatic infix functions and operator overloads
- Context receivers for advanced scoping
- Better type safety with reified generics
- Improved null safety with non-nullable types by default
- Enhanced readability with more descriptive function names
- Scope functions for cleaner query composition
- Range operations for between queries

Example using the enhanced DSL:

```kotlin
// Basic query with enhanced DSL
data {
    select<Person>() where (Person::age gt 21) orderBy Person::name.asc()
}

// Using operator overloads and infix functions
data {
    select<Person>() where (Person::age > 21) and (Person::name contains "John")
}

// Using range operations
data {
    select<Person>() where (Person::age between 18..65)
}

// Using scope-based query building
store.query {
    filter<Person> { it.age > 21 && it.name.contains("Smith") }
}

// Using context receivers for transactions
with(store) {
    transaction {
        val person = insert(Person(name = "John", age = 30))
        update(person.copy(name = "John Smith"))
    }
}
```

### Kotlin Symbol Processing (KSP)

A new KSP processor has been added to replace deprecated KAPT:

- Much faster compilation times (up to 2x faster than KAPT)
- Better incremental compilation support
- Enhanced IDE integration
- Reduced memory usage during compilation
- Type-safe API for annotation processing

To use KSP instead of KAPT:

```kotlin
// In your build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.0.0-1.0.19"
}

dependencies {
    ksp("io.requery:requery-processor-ksp:1.6.1")
}
```

### Kotlin Coroutines Support

Full Kotlin Coroutines support with Flow API integration:

- Suspending functions for all database operations
- Flow API for reactive queries with backpressure support
- Structured concurrency with coroutine scopes
- Cancellation-aware transaction management
- Thread context optimization with dispatchers
- Integration with other Flow-based libraries

Examples:

```kotlin
// Suspending functions with reified type parameters
val person = coroutineStore.findByKeyAsync<Person>(123)

// Transaction with coroutines and structured concurrency
coroutineStore.withTransaction {
    val person = insertAsync(Person(name = "John", age = 30))
    val address = insertAsync(Address(street = "123 Main St", person = person))
}

// Flow API with transformation operators
coroutineStore.select<Person>()
    .where(Person::age.gt(21))
    .get()
    .map { "${it.name} (${it.age})" }
    .filter { it.contains("Smith") }
    .collect { println(it) }

// Parallel processing with Flow
coroutineStore.select<Person>()
    .get()
    .buffer(10)
    .map { loadPersonDetails(it) }
    .collect { processPersonDetails(it) }
```

## Functional Programming Enhancements

The library now provides enhanced functional programming capabilities:

- Improved immutable type support with data classes
- Better support for transformation functions (map, filter, fold)
- Flow API integration for reactive processing
- Enhanced stream API integration with Java streams
- Improved Optional handling with nullable types
- Function composition for building complex queries

Examples:

```kotlin
// Functional transformations
store.select<Person>()
    .get()
    .map { it.name to it.age }
    .fold(0) { acc, (_, age) -> acc + age }

// Using Result extensions
store.select<Person>()
    .where(Person::active.eq(true))
    .get()
    .use { result -> 
        result.map { it.copy(lastActive = LocalDateTime.now()) }
             .forEach { store.update(it) }
    }
```

## Migration Guide

### Migrating from Java 8 to Java 11+

1. Ensure your JDK is updated to at least version 11
2. Update your project's `sourceCompatibility` and `targetCompatibility` to Java 11
3. If using custom module paths, ensure they're compatible with the new module names
4. Add `requires` statements in your module-info.java file if you have one:

```java
module your.application {
    requires io.requery;
    requires io.requery.kotlin;  // if using Kotlin
}
```

### Migrating from Kotlin 1.x to Kotlin 2.x

1. Update your Kotlin version to 2.0.0 in your build.gradle.kts:
   ```kotlin
   plugins {
       kotlin("jvm") version "2.0.0"
   }
   ```

2. Replace KAPT with KSP for annotation processing:
   ```kotlin
   plugins {
       id("com.google.devtools.ksp") version "2.0.0-1.0.19"
   }
   
   dependencies {
       // Replace kapt with ksp
       ksp("io.requery:requery-processor-ksp:1.6.1")
   }
   ```

3. Use the enhanced DSL for more expressive queries with Kotlin 2.0 features
4. Update JVM target settings to match Java 11+:
   ```kotlin
   tasks.withType<KotlinCompile>().configureEach {
       kotlinOptions {
           jvmTarget = "11"
           languageVersion = "2.0"
           apiVersion = "2.0"
       }
   }
   ```

### Moving from RxJava to Coroutines

Both RxJava and Coroutines are supported, but for new Kotlin projects, Coroutines are recommended:

1. Add kotlinx-coroutines dependencies:
   ```kotlin
   dependencies {
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.0")
   }
   ```

2. Convert ReactiveEntityStore to CoroutineEntityStore:
   ```kotlin
   // From RxJava
   val rxStore: ReactiveEntityStore<MyEntity> = ReactiveEntityStore(dataStore)
   
   // To Coroutines
   val coroutineStore: CoroutineEntityStore<MyEntity> = dataStore.asCoroutineEntityStore()
   ```

3. Replace RxJava patterns with coroutines equivalents:

   ```kotlin
   // RxJava
   rxStore.select(Person::class.java)
      .where(Person::age.gt(21))
      .observable()
      .subscribe { person -> println(person.name) }
   
   // Coroutines
   coroutineScope.launch {
       coroutineStore.select<Person>()
           .where(Person::age.gt(21))
           .get()
           .forEach { person -> println(person.name) }
   }
   ```

4. Convert Completables to suspending functions:

   ```kotlin
   // RxJava
   rxStore.insert(person)
       .subscribe(
           { inserted -> println("Inserted: ${inserted.id}") },
           { error -> println("Error: ${error.message}") }
       )
   
   // Coroutines
   try {
       val inserted = coroutineStore.insertAsync(person)
       println("Inserted: ${inserted.id}")
   } catch (e: Exception) {
       println("Error: ${e.message}")
   }
   ```

## Performance Considerations

- KSP is significantly faster than KAPT for processing annotations (typically 2x faster)
- Coroutines have less overhead than RxJava for simple async operations:
  - Smaller memory footprint (no Observer objects and subscriptions)
  - Less CPU overhead (no subscription management)
  - Better cancellation support with structured concurrency
  
- Java 11+ has better performance characteristics than Java 8:
  - Improved garbage collection with G1GC as default
  - Better JIT compilation
  - Lower memory footprint
  - Improved startup time

- Flow API provides efficient backpressure handling for database operations:
  - Demand-driven architecture that only processes what's needed
  - Buffer control for high-throughput scenarios
  - Integration with structured concurrency for resource cleanup