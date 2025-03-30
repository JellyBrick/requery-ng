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

package io.requery.coroutines

import io.requery.BlockingEntityStore
import io.requery.TransactionIsolation
import io.requery.kotlin.*
import io.requery.meta.Attribute
import io.requery.query.Expression
import io.requery.query.Result
import io.requery.query.Return
import io.requery.query.Scalar
import io.requery.query.Tuple
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Coroutine-based entity store interface that provides suspend functions and Flow-based APIs
 * for interacting with the database with full Kotlin 2.0 support.
 */
interface CoroutineEntityStore<T : Any> : EntityStore<T, Flow<*>> {
    /**
     * The coroutine context to use for database operations
     */
    val coroutineContext: CoroutineContext

    /**
     * Converts this store to a blocking store
     */
    fun toBlocking(): BlockingEntityStore<T>

    /**
     * Finds an entity by its key and returns the result as a nullable entity.
     */
    suspend fun <E : T, K> findByKeyAsync(type: KClass<E>, key: K): E?

    /**
     * Executes a transaction with the given body and returns the result.
     */
    suspend fun <R> transactionAsync(body: suspend CoroutineEntityStore<T>.() -> R): R

    /**
     * Executes a transaction with the given isolation level and body, returning the result.
     */
    suspend fun <R> transactionAsync(
        isolation: TransactionIsolation,
        body: suspend CoroutineEntityStore<T>.() -> R
    ): R

    /**
     * Inserts an entity and returns the result asynchronously.
     */
    suspend fun <E : T> insertAsync(entity: E): E

    /**
     * Inserts multiple entities and returns the results as a Flow.
     */
    fun <E : T> insertFlow(entities: Iterable<E>): Flow<E>

    /**
     * Updates an entity and returns the result asynchronously.
     */
    suspend fun <E : T> updateAsync(entity: E): E

    /**
     * Updates multiple entities and returns the results as a Flow.
     */
    fun <E : T> updateFlow(entities: Iterable<E>): Flow<E>

    /**
     * Upserts an entity and returns the result asynchronously.
     */
    suspend fun <E : T> upsertAsync(entity: E): E

    /**
     * Upserts multiple entities and returns the results as a Flow.
     */
    fun <E : T> upsertFlow(entities: Iterable<E>): Flow<E>

    /**
     * Deletes an entity asynchronously.
     */
    suspend fun <E : T> deleteAsync(entity: E)

    /**
     * Deletes multiple entities asynchronously and returns a Flow that completes when done.
     */
    fun <E : T> deleteFlow(entities: Iterable<E>): Flow<E>

    /**
     * Refreshes an entity and returns the result asynchronously.
     */
    suspend fun <E : T> refreshAsync(entity: E): E

    /**
     * Refreshes an entity on specific attributes asynchronously.
     */
    suspend fun <E : T> refreshAsync(entity: E, vararg attributes: Attribute<*, *>): E

    /**
     * Executes a select query and returns the results as a Flow.
     */
    override fun <E : T> select(type: KClass<E>): KotlinFlowSelection<E>

    /**
     * Executes a select query on specific attributes and returns the results as a Flow.
     */
    override fun <E : T> select(type: KClass<E>, vararg attributes: QueryableAttribute<E, *>): KotlinFlowSelection<E>

    /**
     * Query entities with a predicate function
     */
    fun <E : T> query(type: KClass<E>, predicate: (E) -> Boolean): Flow<E>

    /**
     * Converts a Result to a Flow.
     */
    fun <E> Result<E>.asFlow(): Flow<E>
}

/**
 * Flow-based selection interface extending the standard Selection interface
 * to provide Flow-based operations with Kotlin Coroutines.
 */
interface KotlinFlowSelection<E> : Selection<Flow<E>> {
    /**
     * Gets the first element of the query result as a suspend function.
     */
    suspend fun firstOrNull(): E?
    
    /**
     * Gets the single element of the query result as a suspend function.
     * Throws an exception if there is not exactly one element.
     */
    suspend fun single(): E
    
    /**
     * Gets the single element of the query result as a suspend function.
     * Returns null if there are no elements, throws an exception if there is more than one element.
     */
    suspend fun singleOrNull(): E?
    
    /**
     * Converts the query result to a list as a suspend function.
     */
    suspend fun toList(): List<E>
    
    /**
     * Executes the query and returns the count.
     */
    suspend fun count(): Int
    
    /**
     * Maps the results to a new type using the provided mapper function
     */
    fun <R> map(mapper: suspend (E) -> R): Flow<R>
    
    /**
     * Filters the results using the provided predicate
     */
    fun filter(predicate: suspend (E) -> Boolean): Flow<E>
}