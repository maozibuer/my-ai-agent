package com.agent.memory.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.agent.common.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for caching hot (frequently asked) questions and their answers in Redis.
 * Uses SHA-256 hashing of a composite key (question + kbId) to ensure the same
 * question asked against different knowledge bases gets different cached answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotAnswerCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** TTL for hot answer cache in hours */
    private static final long CACHE_TTL_HOURS = 24;

    /** Sentinel value when no knowledge base is selected */
    private static final String NO_KB = "none";

    /**
     * Builds a composite cache key from question text and knowledge base ID.
     * The same question with different kbId values produces different cache keys,
     * preventing cross-KB cache collisions.
     */
    private String cacheKey(String question, Long kbId) {
        String composite = question + ":" + (kbId != null ? kbId : NO_KB);
        return Constants.REDIS_KEY_HOT_ANSWER + hashQuestion(composite);
    }

    /**
     * Retrieves a cached answer for the given question and knowledge base.
     *
     * @param question the question text
     * @param kbId     the knowledge base ID, or null
     * @return the cached answer, or null if not cached
     */
    public String get(String question, Long kbId) {
        String key = cacheKey(question, kbId);
        Object value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            log.debug("Cache hit for question with kbId={}", kbId);
            return value.toString();
        }
        return null;
    }

    /**
     * Stores an answer in the cache for the given question and knowledge base.
     *
     * @param question the question text
     * @param answer   the answer to cache
     * @param kbId     the knowledge base ID, or null
     */
    public void put(String question, String answer, Long kbId) {
        String key = cacheKey(question, kbId);
        redisTemplate.opsForValue().set(key, answer, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Cached answer for question with kbId={}", kbId);
    }

    /**
     * Checks whether a cached answer exists for the given question and knowledge base.
     *
     * @param question the question text
     * @param kbId     the knowledge base ID, or null
     * @return true if a cached answer exists, false otherwise
     */
    public boolean exists(String question, Long kbId) {
        String key = cacheKey(question, kbId);
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Computes the SHA-256 hash of the given text.
     *
     * @param text the text to hash
     * @return the hex-encoded SHA-256 hash string
     */
    public String hashQuestion(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to hash code
            return String.valueOf(text.hashCode());
        }
    }
}
