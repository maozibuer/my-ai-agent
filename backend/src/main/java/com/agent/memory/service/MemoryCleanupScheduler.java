package com.agent.memory.service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agent.common.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler for cleaning up expired chat session memory in Redis.
 * Runs hourly and scans for chat session keys that are expired or have no TTL,
 * removing them to prevent memory leaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCleanupScheduler {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Periodically scans Redis for chat session keys and removes expired ones.
     * Runs every hour on the hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredMemory() {
        log.info("Starting memory cleanup task");

        String pattern = Constants.REDIS_KEY_CHAT + "*";
        int scanned = 0;
        int deleted = 0;

        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                log.info("No chat session keys found for cleanup");
                return;
            }

            for (String key : keys) {
                scanned++;
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

                // TTL of -2 means the key does not exist (already expired)
                // TTL of -1 means the key has no expiration set - we should clean these up
                if (ttl != null && (ttl == -2 || ttl == -1)) {
                    redisTemplate.delete(key);
                    deleted++;
                    log.debug("Cleaned up key: {} (ttl={})", key, ttl);
                }
            }

            log.info("Memory cleanup complete: scanned={}, deleted={}", scanned, deleted);
        } catch (Exception e) {
            log.error("Memory cleanup task failed", e);
        }
    }
}
