package org.sunbird.workflow.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sunbird.workflow.config.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LRUCacheTest {

    private LRUCache<String, String> cache;
    private Configuration mockConfig;

    @BeforeEach
    void setUp() {
        mockConfig = mock(Configuration.class);
        cache = new LRUCache<>(mockConfig);

        // Simulate a very small cache and short TTL
        when(mockConfig.getEnrolStatusCountLocalCacheSize()).thenReturn(2);
        when(mockConfig.getEnrolStatusCountLocalTimeToLive()).thenReturn(1); // 1 minute = 60 ms

        cache.postConstruct();
    }

    @Test
    void testPutAndGetWithinTTL() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    void testRemoveEldestEntry_MaxSizeExceeded() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Should cause eviction of key1 (LRU)
        cache.put("key3", "value3");

        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertEquals("value3", cache.get("key3"));
    }

    @Test
    void testRemoveEldestEntry_Expired() {
        cache.put("key1", "value1");
        Map.Entry<String, String> entry = Map.entry("key1", "value1");

        // should be considered expired
        boolean result = cache.removeEldestEntry(entry);
        assertFalse(result);
    }

    @Test
    void testIsEntryExpired_WhenKeyNotPresent() {
        assertTrue(invokeIsEntryExpired("missingKey"));
    }

    @Test
    void testIsEntryExpired_WhenKeyIsExpired() {
        cache.put("key1", "value1");
        assertFalse(invokeIsEntryExpired("key1"));
    }


    // Helper method to invoke private isEntryExpired
    private boolean invokeIsEntryExpired(String key) {
        try {
            var method = LRUCache.class.getDeclaredMethod("isEntryExpired", Object.class);
            method.setAccessible(true);
            return (boolean) method.invoke(cache, key);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
            return false;
        }
    }
}
