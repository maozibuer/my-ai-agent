package com.agent.common;

import lombok.Getter;

/**
 * Enumeration of application result codes with HTTP-like status codes and messages.
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_ERROR(500, "Internal Server Error"),
    AI_SERVICE_ERROR(5001, "AI Service Error"),
    KB_SEARCH_ERROR(5002, "Knowledge Base Search Error"),
    TOOL_CALL_ERROR(5003, "Tool Call Error");

    /** Numeric result code */
    private final int code;

    /** Human-readable message */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
