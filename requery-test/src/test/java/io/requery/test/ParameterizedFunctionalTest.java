package io.requery.test;

import io.requery.cache.EntityCacheBuilder;
import io.requery.meta.EntityModel;
import io.requery.sql.Configuration;
import io.requery.sql.ConfigurationBuilder;
import io.requery.sql.EntityDataStore;
import io.requery.sql.SchemaModifier;
import io.requery.sql.TableCreationMode;
import io.requery.sql.platform.Derby;
import io.requery.sql.platform.H2;
import io.requery.sql.platform.HSQL;
import io.requery.sql.platform.MySQL;
import io.requery.sql.platform.Oracle;
import io.requery.sql.Platform;
import io.requery.sql.platform.PostgresSQL;
import io.requery.sql.platform.SQLServer;
import io.requery.sql.platform.SQLite;
import io.requery.test.model.Models;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import javax.sql.CommonDataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Runs the functional tests against several different databases.
 *
 * @author Nikhil Purushe
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ParameterizedFunctionalTest extends FunctionalTest {

    private static Collection<Platform> CI = Arrays.asList(
        new PostgresSQL(),
        new MySQL(),
        new H2(),
        new HSQL(),
        new Derby(),
        new SQLite());

    private static Collection<Platform> ALL = Arrays.asList(
        new Oracle(),
        new SQLServer(),
        new MySQL(),
        new PostgresSQL(),
        new Derby(),
        new SQLite(),
        new H2(),
        new HSQL());

    static Stream<Platform> platforms() {
        return CI.stream(); // ALL.stream();
    }

    private Platform platform;

    void initPlatform(Platform platform) {
        this.platform = platform;
    }

    @BeforeEach
    public void setup() throws SQLException {
        if (platform == null) return;

        CommonDataSource dataSource = DatabaseType.getDataSource(platform);
        EntityModel model = Models.DEFAULT;

        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();
        Configuration configuration = new ConfigurationBuilder(dataSource, model)
            .useDefaultLogging()
            // work around bug reusing prepared statements in xerial sqlite
            .setStatementCacheSize(platform instanceof SQLite ? 0 : 10)
            .setBatchUpdateSize(50)
            .setEntityCache(new EntityCacheBuilder(model)
                .useReferenceCache(true)
                .useSerializableCache(true)
                .useCacheManager(cacheManager)
                .build())
            .build();
        data = new EntityDataStore<>(configuration);
        SchemaModifier tables = new SchemaModifier(configuration);
        try {
            tables.dropTables();
        } catch (Exception e) {
            // expected if 'drop if exists' not supported (so ignore in that case)
            if (!platform.supportsIfExists()) {
                throw e;
            }
        }
        TableCreationMode mode = TableCreationMode.CREATE;
        System.out.println(tables.createTablesString(mode));
        tables.createTables(mode);
    }
}
