package com.agent.auth.service;

import com.agent.common.dto.LoginRequest;
import com.agent.common.dto.LoginResponse;
import com.agent.common.dto.RegisterRequest;
import com.agent.common.dto.UserInfoDTO;

/**
 * Authentication service interface.
 * Provides methods for user login, registration, and current user retrieval.
 */
public interface AuthService {

    /**
     * Authenticates a user and returns a login response with a JWT token.
     *
     * @param req the login request containing username and password
     * @return the login response with token and user info
     */
    LoginResponse login(LoginRequest req);

    /**
     * Registers a new user account.
     *
     * @param req the registration request containing username, password, and email
     */
    void register(RegisterRequest req);

    /**
     * Retrieves the currently authenticated user's information.
     *
     * @return the current user's info DTO
     */
    UserInfoDTO getCurrentUser();
}
