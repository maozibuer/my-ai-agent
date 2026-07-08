package com.agent.common.dto;

import lombok.Data;

/**
 * DTO for login responses.
 */
@Data
public class LoginResponse {

    /** JWT access token */
    private String token;

    /** Token type, always "Bearer" */
    private String tokenType = "Bearer";

    /** Token expiration in seconds */
    private long expiresIn;

    /** Authenticated user information */
    private UserInfoDTO userInfo;
}
