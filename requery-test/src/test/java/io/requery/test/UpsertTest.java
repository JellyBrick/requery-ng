/*
 * Copyright 2016 requery.io
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

import io.requery.Persistable;
import io.requery.cache.EmptyEntityCache;
import io.requery.meta.EntityModel;
import io.requery.sql.Configuration;
import io.requery.sql.ConfigurationBuilder;
import io.requery.sql.EntityDataStore;
import io.requery.sql.Platform;
import io.requery.sql.SchemaModifier;
import io.requery.sql.TableCreationMode;
import io.requery.sql.platform.H2;
import io.requery.sql.platform.MySQL;
import io.requery.sql.platform.SQLite;
import io.requery.test.model3.Event;
import io.requery.test.model3.Models;
import io.requery.test.model3.Place;
import io.requery.test.model3.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import javax.sql.CommonDataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpsertTest {

    static Stream<Platform> data() {
        return Stream.of(
                //new PostgresSQL(), disabled on CI
                new MySQL(),
                new H2(),
                //new HSQL(),
                //new Derby(), // fails because of https://issues.apache.org/jira/browse/DERBY-6656
                new SQLite());
    }

    private EntityDataStore<Persistable> data;
    private Platform platform;

    public UpsertTest(Platform platform) {
        this.platform = platform;
    }

    @BeforeEach
    public void setup() throws SQLException {
        CommonDataSource dataSource = DatabaseType.getDataSource(platform);
        EntityModel model = Models.MODEL3;

        Configuration configuration = new ConfigurationBuilder(dataSource, model)
                .useDefaultLogging()
                .setEntityCache(new EmptyEntityCache())
                .setWriteExecutor(Executors.newSingleThreadExecutor())
                .build();

        SchemaModifier tables = new SchemaModifier(configuration);
        tables.createTables(TableCreationMode.DROP_CREATE);
        System.out.println(tables.createTablesString(TableCreationMode.DROP_CREATE));
        data = new EntityDataStore<>(configuration);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testInsertOneToManyInsert(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event = new Event();
        UUID id = UUID.randomUUID();
        event.setId(id);
        event.setName("test");
        Tag t1 = new Tag();
        t1.setId(UUID.randomUUID());
        Tag t2 = new Tag();
        t2.setId(UUID.randomUUID());
        event.getTags().add(t1);
        event.getTags().add(t2);
        data.insert(event);
        HashSet<Tag> set = new HashSet<>(event.getTags());
        assertEquals(2, set.size());
        assertTrue(set.containsAll(Arrays.asList(t1, t2)));
        assertEquals(2, data.select(Tag.class).get().toList().size());
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertInsert(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event = new Event();
        UUID id = UUID.randomUUID();
        event.setId(id);
        event.setName("test");

        data.upsert(event);
        Event found = data.findByKey(Event.class, id);
        assertEquals(event.getId(), found.getId());
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertOneToMany(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event = new Event();
        event.setId(UUID.randomUUID());
        Place place = new Place();
        place.setId(UUID.randomUUID().toString());
        place.setName("place");
        place.getEvents().add(event);
        data.upsert(place);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertManyToMany(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event1 = new Event();
        event1.setId(UUID.randomUUID());
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.getEvents().add(event1);
        data.upsert(tag);
        Event event2 = new Event();
        event2.setId(UUID.randomUUID());
        tag.getEvents().add(event2);
        data.upsert(event2);
        data.upsert(tag);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertInsertOneToMany(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event = new Event();
        UUID id = UUID.randomUUID();
        event.setId(id);

        data.upsert(event);
        assertNotNull(event);

        Event event1 = new Event();
        event1.setId(id);
        Place place = new Place();
        place.setId(UUID.randomUUID().toString());
        place.setName("place");
        place.getEvents().add(event1);
        data.insert(place);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertOneToManyEmptyCollection(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event1 = new Event();
        event1.setId(UUID.randomUUID());
        Place place = new Place();
        place.setId(UUID.randomUUID().toString());
        place.setName("place");
        place.getEvents().add(event1);
        place.getEvents().clear();
        data.upsert(place);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpsertUpdate(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Event event = new Event();
        UUID id = UUID.randomUUID();
        event.setId(id);
        event.setName("event1");

        data.insert(event);

        Event event2 = new Event();
        event2.setId(id);
        event2.setName("event2");
        data.upsert(event2);

        List<Event> events = data.select(Event.class).get().toList();
        assertTrue(events.size() == 1);
        assertEquals("event2", events.iterator().next().getName());
    }
}