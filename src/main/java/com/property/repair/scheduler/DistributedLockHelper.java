package com.property.repair.scheduler;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedLockHelper {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    public DistributedLockHelper(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Try to acquire a distributed lock.
     *
     * @param lockKey       the lock key
     * @param requestId     unique identifier for this lock holder
     * @param expireSeconds lock expiration time in seconds
     * @return true if lock acquired successfully
     */
    public boolean tryLock(String lockKey, String requestId, long expireSeconds) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, requestId, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Release a distributed lock using Lua script to ensure atomicity.
     * Only releases the lock if the current holder matches the requestId.
     *
     * @param lockKey   the lock key
     * @param requestId unique identifier for the lock holder
     * @return true if lock released successfully
     */
    public boolean releaseLock(String lockKey, String requestId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(lockKey), requestId);
        return result != null && result == 1L;
    }
}
