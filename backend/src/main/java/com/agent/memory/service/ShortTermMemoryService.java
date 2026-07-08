package com.agent.memory.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.agent.common.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing short-term conversation memory in Redis.
 * Messages are stored in a ZSET keyed by session ID, with timestamps as scores
 * for chronological ordering. A 30-minute TTL is applied to each session key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortTermMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** TTL for conversation memory in minutes */
    private static final long MEMORY_TTL_MINUTES = 30;

    /** Maximum number of messages to retrieve */
    private static final int MAX_HISTORY_MESSAGES = 10;

    /**
     * Saves a message to the conversation memory for the given session.
     * The message is stored as a JSON object with "role" and "content" fields
     * in a Redis ZSET, with the current timestamp as the score.
     *
     * @param sessionId the chat session identifier
     * @param role      the message role ("user" or "assistant")
     * @param content   the message content
     */
    public void saveMessage(String sessionId, String role, String content) {
        String key = Constants.REDIS_KEY_CHAT + sessionId + ":context";

        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", String.valueOf(System.currentTimeMillis()));

        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, message, score);

        // Set TTL to 30 minutes
        redisTemplate.expire(key, MEMORY_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Saved {} message to session {}", role, sessionId);
    }

    /**
     * Retrieves the conversation history for the given session.
     * Returns the most recent messages, up to a maximum of 10.
     *
     * @param sessionId the chat session identifier
     * @return a list of message maps, each containing "role", "content", and "timestamp"
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        String key = Constants.REDIS_KEY_CHAT + sessionId + ":context";

        Set<Object> rawMessages = redisTemplate.opsForZSet()
                .reverseRange(key, 0, MAX_HISTORY_MESSAGES - 1);

        if (rawMessages == null || rawMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // Reverse to get chronological order (oldest first)
        List<Map<String, String>> history = new ArrayList<>();
        for (Object raw : rawMessages) {
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> msg = (Map<String, String>) raw;
                history.add(msg);
            }
        }
        java.util.Collections.reverse(history);

        log.debug("Retrieved {} messages from session {}", history.size(), sessionId);
        return history;
    }

    /**
     * Clears all conversation memory for the given session.
     *
     * @param sessionId the chat session identifier
     */
    public void clearMemory(String sessionId) {
        String key = Constants.REDIS_KEY_CHAT + sessionId + ":context";
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Cleared memory for session {}: deleted={}", sessionId, deleted);
    }
}
