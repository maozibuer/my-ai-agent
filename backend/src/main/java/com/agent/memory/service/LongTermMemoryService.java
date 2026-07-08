package com.agent.memory.service;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.agent.common.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing long-term user memory in Redis.
 * Stores user facts/preferences as Redis lists and vectors as JSON strings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Appends a fact about the user to their long-term memory.
     *
     * @param userId the user identifier
     * @param fact   the fact to store
     */
    public void saveFact(String userId, String fact) {
        String key = Constants.REDIS_KEY_MEMORY + userId + ":facts";
        redisTemplate.opsForList().rightPush(key, fact);
        log.debug("Saved fact for user {}: {}", userId, fact);
    }

    /**
     * Retrieves all stored facts for a user.
     *
     * @param userId the user identifier
     * @return a list of stored facts
     */
    public List<String> getFacts(String userId) {
        String key = Constants.REDIS_KEY_MEMORY + userId + ":facts";
        List<Object> rawFacts = redisTemplate.opsForList().range(key, 0, -1);

        if (rawFacts == null || rawFacts.isEmpty()) {
            return List.of();
        }

        return rawFacts.stream()
                .map(Object::toString)
                .toList();
    }

    /**
     * Stores a vector embedding associated with a user as a JSON string.
     *
     * @param userId the user identifier
     * @param vector the float vector to store
     */
    public void saveVector(String userId, float[] vector) {
        String key = Constants.REDIS_KEY_MEMORY + userId + ":vector";
        try {
            String json = objectMapper.writeValueAsString(vector);
            redisTemplate.opsForValue().set(key, json);
            log.debug("Saved vector for user {} (dim={})", userId, vector.length);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vector for user {}", userId, e);
        }
    }

    /**
     * Retrieves the vector embedding associated with a user.
     *
     * @param userId the user identifier
     * @return the stored float vector, or null if not found
     */
    public float[] getVector(String userId) {
        String key = Constants.REDIS_KEY_MEMORY + userId + ":vector";
        Object raw = redisTemplate.opsForValue().get(key);

        if (raw == null) {
            return null;
        }

        try {
            return objectMapper.readValue(raw.toString(), float[].class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize vector for user {}", userId, e);
            return null;
        }
    }
}
