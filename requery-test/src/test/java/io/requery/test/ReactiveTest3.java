/*
 * Copyright 2017 requery.io
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

package io.requery.test;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.requery.Persistable;
import io.requery.cache.EntityCacheBuilder;
import io.requery.meta.EntityModel;
import io.requery.query.Result;
import io.requery.reactivex3.ReactiveEntityStore;
import io.requery.reactivex3.ReactiveResult;
import io.requery.reactivex3.ReactiveSupport;
import io.requery.sql.*;
import io.requery.sql.platform.HSQL;
import io.requery.test.model.Person;
import io.requery.test.model.Phone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import javax.sql.CommonDataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ReactiveTest3 extends RandomData {

    protected ReactiveEntityStore<Persistable> data;

    @Before
    public void setup() throws SQLException {
        Platform platform = new HSQL();
        CommonDataSource dataSource = io.requery.test.DatabaseType.getDataSource(platform);
        EntityModel model = io.requery.test.model.Models.DEFAULT;

        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();
        Configuration configuration = new ConfigurationBuilder(dataSource, model)
            .useDefaultLogging()
            .setWriteExecutor(Executors.newSingleThreadExecutor())
            .setEntityCache(new EntityCacheBuilder(model)
                .useReferenceCache(true)
                .useSerializableCache(true)
                .useCacheManager(cacheManager)
                .build())
            .build();

        SchemaModifier tables = new SchemaModifier(configuration);
        tables.createTables(TableCreationMode.DROP_CREATE);
        data = ReactiveSupport.toReactiveStore(new EntityDataStore<>(configuration));
    }

    @After
    public void teardown() {
        if (data != null) {
            data.close();
        }
    }

    @Test
    public void testInsert() throws Exception {
        Person person = randomPerson();
        final CountDownLatch latch = new CountDownLatch(1);
        data.insert(person).subscribe(person1 -> {
            assertTrue(person1.getId() > 0);
            Person cached = data.select(Person.class)
                .where(Person.ID.equal(person1.getId())).get().first();
            assertSame(cached, person1);
            latch.countDown();
        });
        latch.await();
    }

    @Test
    public void testDelete() {
        Person person = randomPerson();
        data.insert(person).blockingGet();
        data.delete(person).blockingAwait();
        Person cached = data.select(Person.class)
            .where(Person.ID.equal(person.getId())).get().firstOrNull();
        assertNull(cached);
    }

    @Test
    public void testInsertCount() {
        Person person = randomPerson();
        Observable.just(person)
            .concatMap((Function<Person, Observable<Person>>) person1 -> data.insert(person1).toObservable());
        Person p = data.insert(person).blockingGet();
        assertTrue(p.getId() > 0);
        int count = data.count(Person.class).get().single().blockingGet();
        assertEquals(1, count);
    }

    @Test
    public void testInsertOneToMany() {
        final Person person = randomPerson();
        data.insert(person).map(person1 -> {
            Phone phone1 = randomPhone();
            phone1.setOwner(person1);
            return phone1;
        }).flatMap((Function<Phone, Single<?>>) phone -> data.insert(phone)).blockingGet();
        assertEquals(1, person.getPhoneNumbers().toList().size());
    }

    @Test
    public void testQueryEmpty() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        data.select(Person.class).get().observable()
                .subscribe(person -> Assert.fail(), throwable -> Assert.fail(), () -> latch.countDown());
        if (!latch.await(1, TimeUnit.SECONDS)) {
            Assert.fail();
        }
    }

    @Test
    public void testQueryObservable() {
        for (int i = 0; i < 30; i++) {
            Person person = randomPerson();
            data.insert(person).blockingGet();
        }
        final List<Person> people = new ArrayList<>();
        data.select(Person.class).limit(50).get()
            .observable()
            .subscribe(person -> people.add(person));
        assertEquals(30, people.size());
    }

    @Test
    public void testQuerySelfObservable() {
        final AtomicInteger count = new AtomicInteger();
        data.select(Person.class).get().observableResult().subscribe(
                (Consumer<Result<Person>>) persons -> count.incrementAndGet());
        data.insert(randomPerson()).blockingGet();
        data.insert(randomPerson()).blockingGet();
        assertEquals(3, count.get());
    }

    @Test
    public void testQuerySelfObservableMap() {
        final AtomicInteger count = new AtomicInteger();
        Disposable disposable = data.select(Person.class).limit(2).get().observableResult()
            .flatMap((Function<ReactiveResult<Person>, Observable<Person>>) persons -> persons.observable()).subscribe(new Consumer<Person>() {
                @Override
                public void accept(Person persons) {
                    count.incrementAndGet();
                }
            });
        data.insert(randomPerson()).blockingGet();
        data.insert(randomPerson()).blockingGet();
        assertEquals(3, count.get());
        disposable.dispose();
    }

    @Test
    public void testSelfObservableDelete() {
        final AtomicInteger count = new AtomicInteger();
        Disposable disposable = data.select(Person.class).get().observableResult().subscribe(
                (Consumer<Result<Person>>) persons -> count.incrementAndGet());
        data.insert(randomPerson()).blockingGet();
        count.set(0);
        data.delete(Person.class).get().single().blockingGet();
        assertEquals(1, count.get());
        disposable.dispose();
    }
} 