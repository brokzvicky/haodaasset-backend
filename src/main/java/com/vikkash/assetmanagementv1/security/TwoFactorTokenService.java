package com.vikkash.assetmanagementv1.security;

import com.vikkash.assetmanagementv1.exception.OtpException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues a short-lived, opaque "challenge token" once an admin has correctly
 * entered their username + password but still needs to complete email-OTP
 * two-factor verification. The React login page holds onto this token for
 * the lifetime of the 2FA step and submits it (instead of the password
 * again) to /verify-login-otp and /resend-login-otp.
 *
 * Deliberately mirrors {@link PasswordResetTokenService}'s in-memory,
 * never-persisted design, but distinguishes "peek" (look up without
 * invalidating — used for resend and for the OTP check itself, so a wrong
 * code doesn't kill the whole login attempt) from "consume" (invalidate —
 * used only once the OTP has actually been verified, so the token can't be
 * replayed to mint a second session).
 */
@Service
public class TwoFactorTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public String issue(String username) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new Entry(username, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    /** Looks up the username for a token without invalidating it. */
    public String peek(String token) {
        Entry entry = tokens.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            tokens.remove(token);
            throw new OtpException("Your login session has expired. Please sign in again.");
        }
        return entry.username();
    }

    /** Validates and permanently invalidates the token, returning the username it was issued for. */
    public String consume(String token) {
        Entry entry = tokens.remove(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            throw new OtpException("Your login session has expired. Please sign in again.");
        }
        return entry.username();
    }

    private record Entry(String username, Instant expiresAt) {
    }
}
