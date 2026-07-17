package com.vikkash.assetmanagementv1.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the "Authorization: Bearer <token>" header on every request, validates
 * the JWT, and (if valid) populates the SecurityContext with the caller's
 * subject and role — enabling role-based access checks downstream.
 *
 * Deliberately does NOT log the token value or user credentials — only the
 * request URI and outcome at DEBUG level to avoid leaking sensitive data.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String token = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (request.getRequestURI().endsWith("/pulse/notifications/stream")) {
            // Browser EventSource can't set custom headers, so the SSE stream
            // accepts the token as a query param instead: ?token=<jwt>
            String qp = request.getParameter("token");
            if (qp != null && !qp.isBlank()) token = qp;
        }

        if (token != null) {

            if (jwtUtil.isTokenValid(token)) {
                String subject = jwtUtil.extractSubject(token);
                String role    = jwtUtil.extractRole(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authenticated: subject={} role={} uri={}", subject, role, request.getRequestURI());

            } else {
                log.debug("JWT validation failed for URI: {}", request.getRequestURI());
            }
        }
        // No else-log here: pre-flight OPTIONS and public endpoints legitimately
        // have no Authorization header and spamming the log for those adds noise.

        filterChain.doFilter(request, response);
    }
}
