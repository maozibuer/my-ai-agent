package com.agent.common;

/**
 * Application-wide static constants.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    /** JWT header name */
    public static final String JWT_HEADER = "Authorization";

    /** JWT token prefix */
    public static final String JWT_PREFIX = "Bearer ";

    /** JWT token time-to-live in seconds (24 hours) */
    public static final long TOKEN_TTL = 86400L;

    // ===== Redis key prefixes =====

    /** Redis key prefix for chat sessions */
    public static final String REDIS_KEY_CHAT = "chat:";

    /** Redis key prefix for hot answers cache */
    public static final String REDIS_KEY_HOT_ANSWER = "hot:answer:";

    /** Redis key prefix for conversation memory */
    public static final String REDIS_KEY_MEMORY = "memory:";

    /** Redis key prefix for knowledge base cache */
    public static final String REDIS_KEY_KNOWLEDGE = "knowledge:";

    // ===== Pagination =====

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 10;
}
