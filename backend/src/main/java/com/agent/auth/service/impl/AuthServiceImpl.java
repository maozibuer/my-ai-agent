package com.agent.auth.service.impl;

import com.agent.auth.JwtTokenProvider;
import com.agent.auth.entity.Role;
import com.agent.auth.entity.User;
import com.agent.auth.mapper.UserMapper;
import com.agent.auth.service.AuthService;
import com.agent.common.BusinessException;
import com.agent.common.Constants;
import com.agent.common.ResultCode;
import com.agent.common.dto.LoginRequest;
import com.agent.common.dto.LoginResponse;
import com.agent.common.dto.RegisterRequest;
import com.agent.common.dto.UserInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AuthService}.
 * Handles user authentication, registration, and current user retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginResponse login(LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(userDetails);

        User user = userMapper.selectByUsername(req.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(Constants.TOKEN_TTL);
        response.setUserInfo(convertToUserInfo(user));

        log.info("User logged in successfully: {}", req.getUsername());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void register(RegisterRequest req) {
        User existingUser = userMapper.selectByUsername(req.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setRole(Role.USER);
        user.setDeleted(false);

        userMapper.insert(user);
        log.info("User registered successfully: {}", req.getUsername());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserInfoDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Not authenticated");
        }

        String username = authentication.getName();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "User not found");
        }

        return convertToUserInfo(user);
    }

    /**
     * Converts a User entity to a UserInfoDTO.
     *
     * @param user the user entity
     * @return the user info DTO
     */
    private UserInfoDTO convertToUserInfo(User user) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().getCode());
        dto.setAvatar(user.getAvatar());
        return dto;
    }
}
