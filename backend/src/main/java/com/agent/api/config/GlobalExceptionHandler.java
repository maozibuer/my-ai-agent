package com.agent.api.config;

import java.util.stream.Collectors;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.agent.common.BusinessException;
import com.agent.common.Result;
import com.agent.common.ResultCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for REST API endpoints.
 * Catches and processes business exceptions, validation errors,
 * and unexpected exceptions, returning appropriate Result responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles authentication-related exceptions (bad credentials, user not found, etc.)
     * and returns a 401 UNAUTHORIZED status so the frontend can distinguish
     * business errors from request failures.
     *
     * @param e the authentication exception
     * @return a Result with error details and HTTP 401
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication failed: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
    }

    /**
     * Handles business-level exceptions.
     *
     * @param e the business exception
     * @return a Result with the error code and message from the exception
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}",
                e.getResultCode().getCode(), e.getMessage());
        return Result.error(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * Handles bean validation exceptions.
     *
     * @param e the validation exception
     * @return a Result with a validation error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errorMessage);
        return Result.error(ResultCode.BAD_REQUEST.getCode(),
                "Validation failed: " + errorMessage);
    }

    /**
     * Handles all other unexpected exceptions.
     *
     * @param e the unexpected exception
     * @return a Result with a generic internal error message
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_ERROR.getCode(),
                "Internal server error");
    }
}
