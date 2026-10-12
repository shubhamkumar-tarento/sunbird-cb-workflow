package org.sunbird.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@Component
@RequiredArgsConstructor
public class RedisCacheMgr {

    private static Integer cache_ttl = 84600;

    private static final String CACHE_KEY_VALUE_LOG_PREFIX = "Cache_key_value ";

    private final JedisPool jedisPool;

    ObjectMapper objectMapper = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(RedisCacheMgr.class);


    public void putCache(String key, String[] values, Integer ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            if(null == ttl)
                ttl = cache_ttl;
            jedis.sadd(Constants.REDIS_COMMON_KEY + key, values);
            jedis.expire(Constants.REDIS_COMMON_KEY + key, ttl);
            logger.debug("{}{}{} is saved in redis", CACHE_KEY_VALUE_LOG_PREFIX, Constants.REDIS_COMMON_KEY, key);
        } catch (Exception e) {
            logger.error("An error occurred while saving data into Redis",e);
        }
    }

    public boolean keyExists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(Constants.REDIS_COMMON_KEY + key);
        } catch (Exception e) {
            logger.error("An Error Occurred while fetching value from Redis", e);
            return false;
        }
    }

    public boolean valueExists(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(Constants.REDIS_COMMON_KEY + key, value);
        } catch (Exception e) {
            logger.error("An Error Occurred while fetching value from Redis", e);
            return false;
        }
    }

    public void putInBasicProfileCache(String key, String data, int ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, data);
            jedis.expire(key, ttl);
            logger.debug("{}{}{} is saved in redis with ttl={}s", CACHE_KEY_VALUE_LOG_PREFIX, Constants.BASIC_PROFILE_KEY, key, ttl);
        } catch (Exception e) {
            logger.error("An Error Occurred while putInBasicProfileCache to Redis", e);
        }
    }

    public String getContentFromCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("An Error Occurred while getContentFromCache", e);
            return null;
        }
    }

    public boolean deleteCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            logger.debug("{}{} is deleted from redis", CACHE_KEY_VALUE_LOG_PREFIX, key);
            return true;
        } catch (Exception e) {
            logger.error("Failed to evict the cache", e);
            return false;
        }
    }
}
