package io.requery.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import io.requery.Converter;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by mluchi on 05/04/2017.
 */

public abstract class AbstractConverterTest<T extends Converter<FROM, TO>, FROM, TO> {

    public abstract T getConverter();

    /**
     * Get the map containing all the conversions to test.
     * Note: the map should have unique values, so that converter can be tested in both directions.
     *
     * @return the map of test cases.
     */
    public abstract Map<FROM, TO> getTestCases();

    @Test
    public void testConvertToPersisted() {
        Map<FROM, TO> testCases = getTestCases();
        for (FROM from : testCases.keySet()) {
            TO expectedConvertedValue = testCases.get(from);
            TO convertedValue = getConverter().convertToPersisted(from);

            assertEquals(expectedConvertedValue, convertedValue);
        }
    }

    @Test
    public void testConvertToMapped() {
        Map<TO, FROM> testCases = new HashMap<>();
        for (Map.Entry<FROM, TO> entry : getTestCases().entrySet()) {
            testCases.put(entry.getValue(), entry.getKey());
        }
        assertTrue(() -> testCases.size() == getTestCases().size(), "Test cases map does not have unique values!");
        for (TO from : testCases.keySet()) {
            FROM expectedConvertedValue = testCases.get(from);
            FROM convertedValue = getConverter().convertToMapped(getType(), from);

            assertEquals(expectedConvertedValue, convertedValue);
        }
    }

    protected abstract Class<? extends FROM> getType();

    protected void assertEquals(Object obj1, Object obj2) {
        Assertions.assertEquals(obj1, obj2);
    }

}
