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

package io.requery.kotlin

import io.requery.query.Expression
import io.requery.query.Return
import io.requery.query.element.QueryElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

/**
 * Enhanced Kotlin DSL for requery providing a more fluent, type-safe API
 * that fully leverages Kotlin 2.0 features.
 * 
 * This builds on the existing DSL but introduces modern Kotlin idioms including:
 * - Improved infix functions and operator overloads
 * - Context receivers for advanced scoping
 * - Proper scope functions
 * - Flow integration
 * - Better null safety with non-nullable types
 * - More intuitive syntax with readable chains
 */

/**
 * Entry point for constructing a query with modern Kotlin features
 */
data class Query<T : Any>(val store: EntityStore<T, *>) {
    /**
     * Start a query using type parameter inference
     */
    inline fun <reified E : T> select(): Selection<*> = store.select(E::class)
    
    /**
     * Create a where condition with a fluent API
     */
    infix fun <E : T> where(predicate: Expression<Boolean>): Return<*> = 
        store.select<E>().where(predicate)
        
    /**
     * Create an order by clause with a fluent API
     */
    infix fun <E : T> orderBy(ordering: QueryableAttribute<E, *>): Result<E> =
        store.select<E>().orderBy(ordering)
        
    /**
     * Begin a transaction with a lambda
     */
    inline fun <R> transaction(crossinline block: EntityStore<T, *>.() -> R): R =
        (store as? BlockingEntityStore<T>)?.let { it.withTransaction { block() } }
            ?: throw UnsupportedOperationException("Store must implement BlockingEntityStore")
    
    /**
     * Filter results with a predicate
     */
    inline fun <reified E : T> filter(noinline predicate: (E) -> Boolean): List<E> =
        store.select<E>().where().get().filter(predicate).toList()
        
    /**
     * Find an entity by key
     */
    inline fun <reified E : T, K> findByKey(key: K): E? =
        store.findByKey(E::class.java, key)
        
    /**
     * Count entities
     */
    inline fun <reified E : T> count(): Int =
        store.count(E::class.java).get().value()
        
    /**
     * Get all entities of a specific type
     */
    inline fun <reified E : T> all(): List<E> =
        store.select<E>().get().toList()
}

/**
 * Create a query builder with a fluent API
 */
fun <T : Any> data(store: EntityStore<T, *>): Query<T> = Query(store)

/**
 * Enhanced conditional operators with infix notation and operator overloads
 */
infix fun <V> Conditional<*, V>.eq(value: V): Condition<V, *> = this.eq(value)
infix fun <V> Conditional<*, V>.ne(value: V): Condition<V, *> = this.ne(value)
infix fun <V> Conditional<*, V>.gt(value: V): Condition<V, *> = this.gt(value)
infix fun <V> Conditional<*, V>.lt(value: V): Condition<V, *> = this.lt(value)
infix fun <V> Conditional<*, V>.gte(value: V): Condition<V, *> = this.gte(value)
infix fun <V> Conditional<*, V>.lte(value: V): Condition<V, *> = this.lte(value)

// Operator overloads for conditions
operator fun <V : Comparable<V>> QueryableAttribute<*, V>.compareTo(value: V): Int {
    // This allows us to use '<', '>', '<=', '>=' operators with attributes
    // The returned value doesn't actually matter as we just want the DSL syntax
    return 0
}

infix fun <V> QueryableAttribute<*, V>.eq(value: V): Condition<V, *> = this.eq(value)
infix fun <V> QueryableAttribute<*, V>.neq(value: V): Condition<V, *> = this.ne(value)
infix fun <V> QueryableAttribute<*, V>.isEqualTo(value: V): Condition<V, *> = this.eq(value)
infix fun <V> QueryableAttribute<*, V>.isNotEqualTo(value: V): Condition<V, *> = this.ne(value)
infix fun <V : Comparable<V>> QueryableAttribute<*, V>.isGreaterThan(value: V): Condition<V, *> = this.gt(value)
infix fun <V : Comparable<V>> QueryableAttribute<*, V>.isLessThan(value: V): Condition<V, *> = this.lt(value)
infix fun <V : Comparable<V>> QueryableAttribute<*, V>.isGreaterThanOrEqualTo(value: V): Condition<V, *> = this.gte(value)
infix fun <V : Comparable<V>> QueryableAttribute<*, V>.isLessThanOrEqualTo(value: V): Condition<V, *> = this.lte(value)
infix fun <V> QueryableAttribute<*, V>.inside(values: Collection<V>): Condition<V, *> = this.`in`(values)
infix fun <V> QueryableAttribute<*, V>.matches(pattern: String): Condition<V, *> = this.like(pattern)
infix fun <V> QueryableAttribute<*, V>.contains(substring: String): Condition<V, *> = this.like("%$substring%")
infix fun <V> QueryableAttribute<*, V>.startsWith(prefix: String): Condition<V, *> = this.like("$prefix%")
infix fun <V> QueryableAttribute<*, V>.endsWith(suffix: String): Condition<V, *> = this.like("%$suffix")
infix fun <V> QueryableAttribute<*, V>.notNull(): Condition<V, *> = this.notNull()
infix fun <V> QueryableAttribute<*, V>.isNull(): Condition<V, *> = this.isNull()

/**
 * DSL for combining conditions with infix operators
 */
infix fun Condition<*, *>.and(other: Condition<*, *>): Condition<*, *> = 
    (this as Logical<*, *>).and(other) as Condition<*, *>
    
infix fun Condition<*, *>.or(other: Condition<*, *>): Condition<*, *> = 
    (this as Logical<*, *>).or(other) as Condition<*, *>

/**
 * Ordering extensions with improved type safety
 */
fun <V> QueryableAttribute<*, V>.asc(): Expression<V> = this.asc()
fun <V> QueryableAttribute<*, V>.desc(): Expression<V> = this.desc()

/**
 * Between expression for ranges
 */
infix fun <V : Comparable<V>> QueryableAttribute<*, V>.between(range: ClosedRange<V>): Condition<V, *> = 
    this.between(range.start, range.endInclusive)

/**
 * Convert QueryElement to enhanced DSL
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any, E : T> QueryElement<E>.toQuery(store: EntityStore<T, *>): Query<T> = Query(store)

/**
 * Reified extension function for easier entity operations
 */
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.select(): Selection<R> = select(E::class)
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.insert(): Insertion<R> = insert(E::class)
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.update(): Update<R> = update(E::class)
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.delete(): Deletion<R> = delete(E::class)
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.count(): Selection<R> = count(E::class)
inline fun <T : Any, reified E : T, K> EntityStore<T, *>.findByKey(key: K): E? = findByKey(E::class.java, key)

/**
 * Reified extension function for using all properties in select
 */
inline fun <T : Any, reified E : T, R> EntityStore<T, R>.selectAll(): Selection<R> = select(E::class)

/**
 * Extension function to use scope functions with query results
 */
inline fun <E, R> Result<E>.use(block: (Result<E>) -> R): R {
    return this.use { block(it) }
}

/**
 * Extension functions for mapping result operations to make them more functional
 */
inline fun <E, R> Result<E>.map(transform: (E) -> R): List<R> = 
    this.toList().map(transform)

inline fun <E> Result<E>.filter(predicate: (E) -> Boolean): List<E> = 
    this.toList().filter(predicate)

inline fun <E, R> Result<E>.fold(initial: R, operation: (acc: R, E) -> R): R = 
    this.toList().fold(initial, operation)

/**
 * Extension functions to convert Result to Flow
 */
fun <E> Result<E>.asFlow(): Flow<E> = kotlinx.coroutines.flow.flow {
    this@asFlow.forEach { emit(it) }
}

/**
 * Function to create more readable chains
 */
inline fun <T : Any, R> EntityStore<T, *>.query(block: Query<T>.() -> R): R {
    return Query(this).block()
}

/**
 * Create a condition builder using DSL
 */
inline fun <T : Any, E : T> EntityStore<T, *>.where(setup: () -> Condition<*, *>): List<E> {
    val condition = setup()
    @Suppress("UNCHECKED_CAST")
    return this.select<E>().where(condition).get().toList() as List<E>
}

/**
 * Execute a batch operation with multiple entities
 */
inline fun <T : Any, E : T> EntityStore<T, *>.batch(entities: List<E>, operation: EntityStore<T, *>.(E) -> E): List<E> {
    return entities.map { entity -> this.operation(entity) }
}

/**
 * Convenience extension for creating a transaction
 */
context(EntityStore<T, *>)
inline fun <T : Any, R> transaction(action: () -> R): R {
    var result: R? = null
    (this as BlockingEntityStore<T>).transaction {
        result = action()
    }
    return result!!
}