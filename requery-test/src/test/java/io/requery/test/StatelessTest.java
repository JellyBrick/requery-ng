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
import io.requery.sql.platform.SQLite;
import io.requery.test.stateless.Entry;
import io.requery.test.stateless.Models;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.sql.CommonDataSource;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatelessTest {

    static Stream<Platform> data() {
        return Stream.of(new H2(), new SQLite());
    }

    private EntityDataStore<Persistable> data;
    private Platform platform;

    public StatelessTest(Platform platform) {
        this.platform = platform;
    }

    @BeforeEach
    public void setup() throws SQLException {
        CommonDataSource dataSource = DatabaseType.getDataSource(platform);
        EntityModel model = Models.STATELESS;

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
    void testInsert(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Entry event = new Entry();
        UUID id = UUID.randomUUID();
        event.setId(id.toString());
        event.setCreated(new Date());
        event.setFlag1(false);
        event.setFlag2(true);

        data.insert(event);
        Entry found = data.findByKey(Entry.class, id.toString());
        assertEquals(event.getId(), found.getId());
    }

    @ParameterizedTest
    @MethodSource("data")
    void testUpdate(Platform platform) throws SQLException {
        this.platform = platform;
        setup();
        Entry event = new Entry();
        UUID id = UUID.randomUUID();
        event.setId(id.toString());
        event.setCreated(new Date());
        event.setFlag1(false);
        event.setFlag2(true);

        data.insert(event);
        Entry found = data.findByKey(Entry.class, id.toString());
        assertEquals(false, found.isFlag1());
        assertEquals(true, found.isFlag2());

        event.setFlag1(true);
        event.setFlag2(false);
        data.update(event);

        found = data.findByKey(Entry.class, id.toString());
        assertEquals(true, found.isFlag1());
        assertEquals(false, found.isFlag2());
    }
}