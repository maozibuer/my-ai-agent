package com.agent.auth.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.agent.auth.entity.Role;
import com.agent.auth.entity.User;
import com.agent.auth.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security UserDetailsService implementation.
 * Loads user information from the database via {@link UserMapper}
 * and converts it to a Spring Security {@link UserDetails} object.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                true,          // enabled
                true,          // accountNonExpired
                true,          // credentialsNonExpired
                true,          // accountNonLocked
                getAuthorities(user.getRole())
        );
    }

    /**
     * Builds the granted authorities list based on the user's role.
     *
     * @param role the user's role
     * @return list of granted authorities
     */
    private List<GrantedAuthority> getAuthorities(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Add role-based authority (prefixed with ROLE_)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));

        // Add permission-based authorities for ADMIN role
        if (role == Role.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("KB_MANAGE"));
            authorities.add(new SimpleGrantedAuthority("USER_MANAGE"));
            authorities.add(new SimpleGrantedAuthority("CHAT_HISTORY_DELETE"));
        }
        // Common permissions for all users
        authorities.add(new SimpleGrantedAuthority("CHAT"));
        authorities.add(new SimpleGrantedAuthority("KB_SEARCH"));
        authorities.add(new SimpleGrantedAuthority("CHAT_HISTORY_VIEW"));

        return authorities;
    }
}
