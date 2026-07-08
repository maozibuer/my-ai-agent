package com.agent.common;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified API response wrapper.
 *
 * @param <T> the type of the data payload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Response code */
    private int code;

    /** Response message */
    private String message;

    /** Response data */
    private T data;

    /**
     * Returns a success result with no data.
     *
     * @param <T> the type of the data payload
     * @return a success result
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * Returns a success result with data.
     *
     * @param <T>   the type of the data payload
     * @param data  the data payload
     * @return a success result with data
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * Returns a success result with a custom message and data.
     *
     * @param <T>      the type of the data payload
     * @param message  the custom message
     * @param data     the data payload
     * @return a success result with message and data
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * Returns an error result with a custom message.
     *
     * @param <T>      the type of the data payload
     * @param message  the error message
     * @return an error result
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    /**
     * Returns an error result with a custom code and message.
     *
     * @param <T>      the type of the data payload
     * @param code     the error code
     * @param message  the error message
     * @return an error result
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
