package io.requery.test;

import io.requery.EntityCache;
import io.requery.cache.EntityCacheBuilder;
import io.requery.test.model.Address;
import io.requery.test.model.AddressType;
import io.requery.test.model.Models;
import io.requery.test.model.Person;
import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EntityCacheTest {

    @Test
    public void testSerializeGetPut() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();

        EntityCache cache = new EntityCacheBuilder(Models.DEFAULT)
                .useReferenceCache(false)
                .useSerializableCache(true)
                .useCacheManager(cacheManager)
                .build();

        Person p = new Person();
        p.setName("Alice");
        p.setUUID(UUID.randomUUID());
        p.setAddress(new Address());
        p.getAddress().setType(AddressType.HOME);

        int id = 100;
        cache.put(Person.class, id, p);

        Person d = cache.get(Person.class, id);
        assertNotNull(d);
        assertNotSame(p, d);
        assertEquals(p.getName(), d.getName());
        assertEquals(p.getUUID(), d.getUUID());
    }
}
