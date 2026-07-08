package com.agent.api.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.auth.service.AuthService;
import com.agent.common.Result;
import com.agent.common.dto.LoginRequest;
import com.agent.common.dto.LoginResponse;
import com.agent.common.dto.RegisterRequest;
import com.agent.common.dto.UserInfoDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for authentication endpoints.
 * Handles user login, registration, and current user information retrieval.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for user authentication and registration")
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param request the login request containing username and password
     * @return a Result containing the login response with token and user info
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates a user and returns a JWT token")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for user: {}", request.getUsername());
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * Registers a new user account.
     *
     * @param request the registration request containing username, password, and email
     * @return a Result indicating success or failure
     */
    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Registers a new user account")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for user: {}", request.getUsername());
        authService.register(request);
        return Result.success();
    }

    /**
     * Retrieves the currently authenticated user's information.
     *
     * @return a Result containing the user info DTO
     */
    @GetMapping("/info")
    @Operation(summary = "Get current user", description = "Retrieves the authenticated user's information")
    public Result<UserInfoDTO> getCurrentUser() {
        UserInfoDTO userInfo = authService.getCurrentUser();
        return Result.success(userInfo);
    }
}
