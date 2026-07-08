package com.agent.auth;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT authentication filter that extracts and validates the Bearer token
 * from the Authorization header (or query parameter for SSE) on every request.
 * <p>
 * If a valid token is found, the authenticated user is loaded and set
 * into the {@link SecurityContextHolder}.
 * <p>
 * ASYNC dispatches (triggered by Tomcat after SSE streaming completes) are
 * skipped entirely — the SecurityContext is already gone at that point and
 * the SecurityConfig permits ASYNC dispatches unconditionally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Skip this filter for ASYNC re-dispatches.
     * When Tomcat finishes an async SSE response it re-invokes the servlet via
     * an ASYNC dispatch. The JWT token is no longer available on those re-dispatches,
     * so we must not try to authenticate them — Spring Security is configured to
     * permit ASYNC dispatches unconditionally (see SecurityConfig).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getTokenFromRequest(request);

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the request.
     * <ol>
     *   <li>Authorization header — {@code Bearer <token>} (standard REST calls)</li>
     *   <li>Query parameter {@code token} — required for SSE because the browser's
     *       {@code EventSource} API cannot set custom headers</li>
     * </ol>
     *
     * @param request the HTTP request
     * @return the raw JWT string, or {@code null} if not present
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 1. Try Authorization header (standard for non-SSE requests)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 2. Fallback to query parameter for SSE (EventSource cannot set headers)
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}
