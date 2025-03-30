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

import io.requery.Transaction
import io.requery.TransactionIsolation
import io.requery.kotlin.*
import io.requery.meta.Attribute
import io.requery.query.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Kotlin Coroutine implementation for the [CoroutineEntityStore] interface that provides 
 * enhanced Flow-based APIs using modern Kotlin features.
 */
class KotlinCoroutineEntityStore<T : Any>(
    private val store: BlockingEntityStore<T>,
    override val coroutineContext: CoroutineContext = Dispatchers.IO
) : CoroutineEntityStore<T> {

    override fun close() = store.close()

    override suspend fun <E : T, K> findByKeyAsync(type: KClass<E>, key: K): E? = 
        withContext(coroutineContext) {
            store.findByKey(type, key)
        }

    override suspend fun <R> transactionAsync(body: suspend CoroutineEntityStore<T>.() -> R): R =
        withContext(coroutineContext) {
            store.transaction.use {
                body()
            }
        }

    override suspend fun <R> transactionAsync(
        isolation: TransactionIsolation,
        body: suspend CoroutineEntityStore<T>.() -> R
    ): R = withContext(coroutineContext) {
        var transaction: Transaction? = null
        try {
            transaction = store.transaction
            transaction.setIsolation(isolation)
            transaction.begin()
            val result = body()
            transaction.commit()
            return@withContext result
        } catch (e: Exception) {
            transaction?.rollback()
            throw e
        } finally {
            transaction?.close()
        }
    }

    override suspend fun <E : T> insertAsync(entity: E): E = 
        withContext(coroutineContext) {
            store.insert(entity)
        }

    override fun <E : T> insertFlow(entities: Iterable<E>): Flow<E> = flow {
        withContext(coroutineContext) {
            store.insert(entities).forEach { emit(it) }
        }
    }

    override suspend fun <E : T> updateAsync(entity: E): E = 
        withContext(coroutineContext) {
            store.update(entity)
        }

    override fun <E : T> updateFlow(entities: Iterable<E>): Flow<E> = flow {
        withContext(coroutineContext) {
            store.update(entities).forEach { emit(it) }
        }
    }

    override suspend fun <E : T> upsertAsync(entity: E): E = 
        withContext(coroutineContext) {
            store.upsert(entity)
        }

    override fun <E : T> upsertFlow(entities: Iterable<E>): Flow<E> = flow {
        withContext(coroutineContext) {
            store.upsert(entities).forEach { emit(it) }
        }
    }

    override suspend fun <E : T> deleteAsync(entity: E) = 
        withContext(coroutineContext) {
            store.delete(entity)
        }

    override fun <E : T> deleteFlow(entities: Iterable<E>): Flow<E> = flow {
        withContext(coroutineContext) {
            val list = entities.toList()
            store.delete(list)
            list.forEach { emit(it) }
        }
    }

    override suspend fun <E : T> refreshAsync(entity: E): E = 
        withContext(coroutineContext) {
            store.refresh(entity)
        }

    override suspend fun <E : T> refreshAsync(entity: E, vararg attributes: Attribute<*, *>): E = 
        withContext(coroutineContext) {
            store.refresh(entity, *attributes)
        }

    override fun <E : T> select(type: KClass<E>): KotlinFlowSelection<E> = 
        FlowSelectionDelegate(store.select(type), coroutineContext)

    override fun <E : T> select(
        type: KClass<E>, 
        vararg attributes: QueryableAttribute<E, *>
    ): KotlinFlowSelection<E> = FlowSelectionDelegate(store.select(type, *attributes), coroutineContext)

    override fun select(vararg expressions: Expression<*>): Selection<Flow<Tuple>> = 
        FlowSelectionDelegate(store.select(*expressions), coroutineContext)

    override fun <E : T> query(type: KClass<E>, predicate: (E) -> Boolean): Flow<E> = flow {
        withContext(coroutineContext) {
            store.select(type.java).get().forEach { entity ->
                if (predicate(entity)) {
                    emit(entity)
                }
            }
        }
    }

    override fun <E : T> insert(type: KClass<E>): Insertion<Flow<Tuple>> = 
        FlowQueryDelegate(store.insert(type), coroutineContext)

    override fun <E : T> insert(type: KClass<E>, vararg attributes: QueryableAttribute<E, *>): InsertInto<out Result<Tuple>> = 
        store.insert(type, *attributes)

    override fun update(): Update<Flow<Int>> = 
        FlowQueryDelegate(store.update(), coroutineContext)

    override fun <E : T> update(type: KClass<E>): Update<Flow<Int>> = 
        FlowQueryDelegate(store.update(type), coroutineContext)

    override fun delete(): Deletion<Flow<Int>> = 
        FlowQueryDelegate(store.delete(), coroutineContext)

    override fun <E : T> delete(type: KClass<E>): Deletion<Flow<Int>> = 
        FlowQueryDelegate(store.delete(type), coroutineContext)

    override fun <E : T> count(type: KClass<E>): Selection<Flow<Int>> = 
        FlowQueryDelegate(store.count(type), coroutineContext)

    override fun count(vararg attributes: QueryableAttribute<T, *>): Selection<Flow<Int>> = 
        FlowQueryDelegate(store.count(*attributes), coroutineContext)

    override fun <E : T> insert(entity: E): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.insert(entity) }, coroutineContext.asExecutor())

    override fun <E : T> insert(entities: Iterable<E>): CompletableFuture<Iterable<E>> = 
        CompletableFuture.supplyAsync({ store.insert(entities) }, coroutineContext.asExecutor())

    override fun <K : Any, E : T> insert(entity: E, keyClass: KClass<K>): CompletableFuture<K> = 
        CompletableFuture.supplyAsync({ store.insert(entity, keyClass) }, coroutineContext.asExecutor())

    override fun <K : Any, E : T> insert(entities: Iterable<E>, keyClass: KClass<K>): CompletableFuture<Iterable<K>> = 
        CompletableFuture.supplyAsync({ store.insert(entities, keyClass) }, coroutineContext.asExecutor())

    override fun <E : T> update(entity: E): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.update(entity) }, coroutineContext.asExecutor())

    override fun <E : T> update(entities: Iterable<E>): CompletableFuture<Iterable<E>> = 
        CompletableFuture.supplyAsync({ store.update(entities) }, coroutineContext.asExecutor())

    override fun <E : T> upsert(entity: E): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.upsert(entity) }, coroutineContext.asExecutor())

    override fun <E : T> upsert(entities: Iterable<E>): CompletableFuture<Iterable<E>> = 
        CompletableFuture.supplyAsync({ store.upsert(entities) }, coroutineContext.asExecutor())

    override fun <E : T> refresh(entity: E): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.refresh(entity) }, coroutineContext.asExecutor())

    override fun <E : T> refresh(entity: E, vararg attributes: Attribute<*, *>): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.refresh(entity, *attributes) }, coroutineContext.asExecutor())

    override fun <E : T> refresh(
        entities: Iterable<E>, 
        vararg attributes: Attribute<*, *>
    ): CompletableFuture<Iterable<E>> = CompletableFuture.supplyAsync({ 
        store.refresh(entities, *attributes) 
    }, coroutineContext.asExecutor())

    override fun <E : T> refreshAll(entity: E): CompletableFuture<E> = 
        CompletableFuture.supplyAsync({ store.refreshAll(entity) }, coroutineContext.asExecutor())

    override fun <E : T> delete(entity: E): CompletableFuture<Void?> = 
        CompletableFuture.supplyAsync({ store.delete(entity) }, coroutineContext.asExecutor())

    override fun <E : T> delete(entities: Iterable<E>): CompletableFuture<Void?> = 
        CompletableFuture.supplyAsync({ store.delete(entities) }, coroutineContext.asExecutor())

    override fun raw(query: String, vararg parameters: Any): Result<Tuple> = 
        store.raw(query, *parameters)

    override fun <E : T> raw(type: KClass<E>, query: String, vararg parameters: Any): Result<E> = 
        store.raw(type, query, *parameters)

    override fun <E : T, K> findByKey(type: KClass<E>, key: K): CompletableFuture<E?> = 
        CompletableFuture.supplyAsync({ store.findByKey(type, key) }, coroutineContext.asExecutor())

    override fun toBlocking(): BlockingEntityStore<T> = store

    override fun <E> Result<E>.asFlow(): Flow<E> = flow {
        withContext(coroutineContext) {
            use { result ->
                result.forEach { emit(it) }
            }
        }
    }
    
    // Extension to convert CoroutineContext to Executor
    private fun CoroutineContext.asExecutor() = object : java.util.concurrent.Executor {
        override fun execute(command: Runnable) {
            CoroutineScope(this@asExecutor).launch {
                command.run()
            }
        }
    }
}

/**
 * Enhanced Flow-based Selection delegate that implements the KotlinFlowSelection interface
 * with improved coroutine and Flow API support.
 */
class FlowSelectionDelegate<E>(
    private val delegate: Return<Result<E>>,
    private val context: CoroutineContext
) : KotlinFlowSelection<E>, Return<Flow<E>> {

    override suspend fun firstOrNull(): E? = 
        withContext(context) {
            delegate.get().firstOrNull()
        }

    override suspend fun single(): E = 
        withContext(context) {
            delegate.get().first()
        }

    override suspend fun singleOrNull(): E? = 
        withContext(context) {
            val result = delegate.get()
            val iterator = result.iterator()
            if (!iterator.hasNext()) {
                return@withContext null
            }
            val element = iterator.next()
            if (iterator.hasNext()) {
                throw IllegalStateException("Query returned more than one element")
            }
            element
        }

    override suspend fun toList(): List<E> = 
        withContext(context) {
            delegate.get().toList()
        }

    override suspend fun count(): Int = 
        withContext(context) {
            delegate.get().count()
        }

    override fun get(): Flow<E> = flow {
        withContext(context) {
            delegate.get().use { result ->
                result.forEach { emit(it) }
            }
        }
    }

    override fun <R> map(mapper: suspend (E) -> R): Flow<R> = flow {
        withContext(context) {
            delegate.get().use { result ->
                result.forEach { entity ->
                    emit(mapper(entity))
                }
            }
        }
    }
    
    override fun filter(predicate: suspend (E) -> Boolean): Flow<E> = flow {
        withContext(context) {
            delegate.get().use { result ->
                result.forEach { entity ->
                    if (predicate(entity)) {
                        emit(entity)
                    }
                }
            }
        }
    }

    override fun from(vararg types: KClass<out Any>): JoinWhereGroupByOrderBy<Flow<E>> = 
        FlowQueryDelegate(delegate.from(*types), context)

    override fun from(vararg types: Class<out Any>): JoinWhereGroupByOrderBy<Flow<E>> = 
        FlowQueryDelegate(delegate.from(*types), context)

    override fun from(vararg queries: io.requery.util.function.Supplier<*>): JoinWhereGroupByOrderBy<Flow<E>> = 
        FlowQueryDelegate(delegate.from(*queries), context)

    override fun join(type: KClass<out Any>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.join(type), context)

    override fun leftJoin(type: KClass<out Any>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.leftJoin(type), context)

    override fun rightJoin(type: KClass<out Any>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.rightJoin(type), context)

    override fun <J> join(query: Return<J>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.join(query), context)

    override fun <J> leftJoin(query: Return<J>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.leftJoin(query), context)

    override fun <J> rightJoin(query: Return<J>): JoinOn<Flow<E>> = 
        FlowQueryDelegate(delegate.rightJoin(query), context)

    override fun where(): Exists<SetGroupByOrderByLimit<Flow<E>>> = 
        FlowQueryDelegate(delegate.where(), context)

    override fun <V> where(condition: Condition<V, *>): WhereAndOr<Flow<E>> = 
        FlowQueryDelegate(delegate.where(condition), context)

    override fun groupBy(vararg expressions: Expression<*>): SetHavingOrderByLimit<Flow<E>> = 
        FlowQueryDelegate(delegate.groupBy(*expressions), context)

    override fun <V> groupBy(expression: Expression<V>): SetHavingOrderByLimit<Flow<E>> = 
        FlowQueryDelegate(delegate.groupBy(expression), context)

    override fun <V> orderBy(expression: Expression<V>): Limit<Flow<E>> = 
        FlowQueryDelegate(delegate.orderBy(expression), context)

    override fun orderBy(vararg expressions: Expression<*>): Limit<Flow<E>> = 
        FlowQueryDelegate(delegate.orderBy(*expressions), context)

    override fun limit(limit: Int): Offset<Flow<E>> = 
        FlowQueryDelegate(delegate.limit(limit), context)

    override fun offset(offset: Int): Return<Flow<E>> = 
        FlowQueryDelegate(delegate.offset(offset), context)

    override fun union(): Selectable<Flow<E>> = 
        FlowQueryDelegate(delegate.union(), context)

    override fun unionAll(): Selectable<Flow<E>> = 
        FlowQueryDelegate(delegate.unionAll(), context)

    override fun intersect(): Selectable<Flow<E>> = 
        FlowQueryDelegate(delegate.intersect(), context)

    override fun except(): Selectable<Flow<E>> = 
        FlowQueryDelegate(delegate.except(), context)

    override fun distinct(): DistinctSelection<Flow<E>> = 
        FlowQueryDelegate(delegate.distinct(), context)
}

/**
 * Enhanced Flow-based Query delegate that implements the QueryDelegate interface
 * with improved coroutine and Flow API support.
 */
class FlowQueryDelegate<E, Q>(
    private val delegate: Q,
    private val context: CoroutineContext
) : QueryDelegate<Flow<E>>, FlowSelectionDelegate<E>(delegate as Return<Result<E>>, context)
        where Q : Return<*>, Q : QueryDelegate<*> {

    @Suppress("UNCHECKED_CAST")
    override fun <R> extend(transformer: io.requery.util.function.Function<*, *>): QueryDelegate<R> {
        val newDelegate = delegate.extend(transformer) as QueryDelegate<*>
        val flowFunction = io.requery.util.function.Function<Result<E>, Flow<E>> { result ->
            flow {
                withContext(context) {
                    result.use { res ->
                        res.forEach { emit(it) }
                    }
                }
            }
        }
        return newDelegate.extend(flowFunction) as QueryDelegate<R>
    }
}

/**
 * Create a coroutine-based entity store from a blocking entity store.
 */
fun <T : Any> BlockingEntityStore<T>.asCoroutineEntityStore(
    context: CoroutineContext = Dispatchers.IO
): CoroutineEntityStore<T> = KotlinCoroutineEntityStore(this, context)

/**
 * Inline reified version of findByKeyAsync
 */
suspend inline fun <T : Any, reified E : T, K> CoroutineEntityStore<T>.findByKeyAsync(key: K): E? =
    findByKeyAsync(E::class, key)

/**
 * Inline reified version of select
 */
inline fun <T : Any, reified E : T> CoroutineEntityStore<T>.select(): KotlinFlowSelection<E> =
    select(E::class)

/**
 * Inline reified version of query
 */
inline fun <T : Any, reified E : T> CoroutineEntityStore<T>.query(noinline predicate: (E) -> Boolean): Flow<E> =
    query(E::class, predicate)

/**
 * Convenient transaction function with coroutines
 */
suspend fun <T : Any, R> CoroutineEntityStore<T>.withTransaction(
    isolation: TransactionIsolation = TransactionIsolation.DEFAULT,
    crossinline block: suspend CoroutineEntityStore<T>.() -> R
): R = transactionAsync(isolation) { block() }

/**
 * Extension function to collect a flow and perform an operation on each item
 */
suspend inline fun <E> Flow<E>.forEach(crossinline action: suspend (E) -> Unit): Unit =
    collect { action(it) }

/**
 * Extension function to convert a Flow to a reactive Publisher
 */
fun <E> Flow<E>.asPublisher(): org.reactivestreams.Publisher<E> =
    kotlinx.coroutines.reactive.asPublisher(this)