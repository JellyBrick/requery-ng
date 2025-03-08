package io.requery.test;

import io.requery.util.ClassMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClassMapTest {

    @Test
    public void testDerivedContains() {
        ClassMap<String> map = new ClassMap<>();
        map.put(CharSequence.class, "test");
        assertTrue(map.containsKey(String.class));
        assertEquals(map.get(String.class), "test");
        assertNull(map.get(Object.class));
    }
}
