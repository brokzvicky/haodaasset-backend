package com.vikkash.assetmanagementv1.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues and validates stateless JWTs. The token carries the subject
 * (username for admin / employeeId for employee) plus a "role" claim that
 * downstream filters and the React app use for route protection.
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey() {
        // HMAC-SHA key derived from the configured secret (must be >= 256 bits for HS256)
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String subject, String role) {
        return generateToken(subject, role, java.util.Set.of());
    }

    /**
     * Same as {@link #generateToken(String, String)} but additionally embeds
     * the caller's fine-grained permission codes (from their assigned
     * {@link com.vikkash.assetmanagementv1.entity.Role}) as a "perms" claim —
     * a comma-joined string, since JJWT's default serializer doesn't need a
     * JSON array module for that. {@link JwtAuthenticationFilter} turns each
     * code into its own {@code SimpleGrantedAuthority} so
     * {@code @PreAuthorize("hasAuthority('ASSETS_WRITE')")} works, and
     * {@code GET /api/auth/me} returns the same list so the frontend can
     * decide which modules to render.
     */
    public String generateToken(String subject, String role, java.util.Collection<String> permissionCodes) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("perms", String.join(",", permissionCodes));

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /** Returns the caller's fine-grained permission codes embedded at login (may be empty). */
    public java.util.List<String> extractPermissions(String token) {
        String raw = extractAllClaims(token).get("perms", String.class);
        if (raw == null || raw.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }
}
