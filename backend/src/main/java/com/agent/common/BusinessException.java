package com.agent.common;

import lombok.Getter;

/**
 * Custom runtime exception carrying a {@link ResultCode}.
 * Used for business-level error handling across the application.
 * code by th3way:2385313282@qq.com
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The result code associated with this exception */
    private final ResultCode resultCode;

    /**
     * Constructs a BusinessException using the message from the given ResultCode.
     *
     * @param resultCode the result code
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * Constructs a BusinessException with a custom message.
     *
     * @param resultCode the result code
     * @param message    the custom error message
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
