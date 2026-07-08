package com.agent.api.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.auth.entity.User;
import com.agent.auth.mapper.UserMapper;
import com.agent.auth.service.AuthService;
import com.agent.common.Result;
import com.agent.common.dto.RegisterRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for user management endpoints.
 * All endpoints require ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User API", description = "Endpoints for user management (admin only)")
public class UserController {

    private final UserMapper userMapper;
    private final AuthService authService;

    /**
     * Creates a new user (admin only).
     * Delegates to AuthService.register() for password encoding and duplicate checks.
     */
    @PostMapping
    @Operation(summary = "Create user", description = "Creates a new user account (admin only)")
    public Result<Void> createUser(@Valid @RequestBody RegisterRequest request) {
        log.info("Admin request to create user: {}", request.getUsername());
        authService.register(request);
        return Result.success();
    }
    /**
     * Lists all users in the system.
     *
     * @return a Result containing a list of all users
     */
    @GetMapping
    @Operation(summary = "List all users", description = "Returns a list of all registered users (admin only)")
    public Result<List<User>> listUsers() {
        log.info("Admin request to list all users");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDeleted, false);
        wrapper.orderByDesc(User::getCreateTime);
        List<User> users = userMapper.selectList(wrapper);
        // Clear passwords for security
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    /**
     * Get a specific user by ID
     *
     * @param id the user ID
     * @return a Result containing the user
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Returns a specific user by ID (admin only)")
    public Result<User> getUser(@PathVariable Long id) {
        log.info("Admin request to get user: {}", id);
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * Update user information
     *
     * @param id the user ID
     * @param user the user data to update
     * @return a Result indicating success
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates user information (admin only)")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody User user) {
        log.info("Admin request to update user: {}", id);
        user.setId(id);
        // Never allow password update via this endpoint
        user.setPassword(null);
        userMapper.updateById(user);
        return Result.success();
    }

    /**
     * Delete a user
     *
     * @param id the user ID
     * @return a Result indicating success
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes a user (soft delete, admin only)")
    public Result<Void> deleteUser(@PathVariable Long id) {
        log.info("Admin request to delete user: {}", id);
        User user = new User();
        user.setId(id);
        user.setDeleted(true);
        userMapper.updateById(user);
        return Result.success();
    }
}
