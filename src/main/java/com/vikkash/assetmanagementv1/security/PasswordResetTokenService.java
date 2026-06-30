package com.vikkash.assetmanagementv1.security;

import com.vikkash.assetmanagementv1.exception.OtpException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues a short-lived, single-use opaque token once an admin has
 * successfully verified their password-reset OTP. The React app then
 * submits this token (instead of the OTP again) to the final
 * "set new password" step. Kept entirely in memory — never persisted.
 */
@Service
public class PasswordResetTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(String email) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new Entry(email, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    /** Validates and consumes the token, returning the email it was issued for. Single-use. */
    public String consume(String token) {
        Entry entry = tokens.remove(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt)) {
            throw new OtpException("Your password reset session has expired. Please verify your email again.");
        }
        return entry.email;
    }

    private record Entry(String email, Instant expiresAt) {
    }
}
