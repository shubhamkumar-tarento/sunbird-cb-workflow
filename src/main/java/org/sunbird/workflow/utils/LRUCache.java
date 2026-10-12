package org.sunbird.workflow.utils;

import org.springframework.stereotype.Component;
import org.sunbird.workflow.config.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LRUCache<K, V> extends LinkedHashMap<K, V> {

    private final transient Configuration conf;
    private  int maxSize;
    private  long maxAgeInMillis;
    private  transient Map<K, Long> entryTimeMap;

    public LRUCache(Configuration conf) {
        super(16, 0.75f, true);
        this.conf = conf;
    }

    @PostConstruct
    public void postConstruct() {
        this.maxSize = conf.getEnrolStatusCountLocalCacheSize();
        this.maxAgeInMillis = (long) conf.getEnrolStatusCountLocalTimeToLive() * 60;
        this.entryTimeMap = new HashMap<>();
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean isMaxSizeExceeded = size() > maxSize;
        if (isMaxSizeExceeded || isEntryExpired(eldest.getKey())) {
            entryTimeMap.remove(eldest.getKey());
            return true;
        }
        return false;
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (value != null) {
            long currentTime = System.currentTimeMillis();
            if((currentTime - entryTimeMap.get(key)) > maxAgeInMillis) {
                entryTimeMap.remove(key);
                value = null;
            }
        }
        return value;
    }

    @Override
    public V put(K key, V value) {
        entryTimeMap.put(key, System.currentTimeMillis());
        return super.put(key, value);
    }

    private boolean isEntryExpired(K key) {
        if (!entryTimeMap.containsKey(key)) {
            return true;  // Entry does not exist, considered expired
        }
        long entryTime = entryTimeMap.get(key);
        long currentTime = System.currentTimeMillis();
        return (currentTime - entryTime) > maxAgeInMillis;
    }

 }
