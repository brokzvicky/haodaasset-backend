package com.vikkash.assetmanagementv1.security;

import com.vikkash.assetmanagementv1.exception.OtpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates, verifies, and tracks one-time passcodes entirely in server
 * memory — OTPs are never written to the database or disk. Used both by
 * Admin Forgot Password and by the Network Credential unlock flow; callers
 * pass a namespaced key (e.g. "pwreset:admin@company.com" or
 * "unlock:admin") so the two flows never collide.
 *
 * Each entry tracks: a BCrypt hash of the 6-digit code (never the
 * plaintext), an expiry timestamp, the number of failed attempts, and when
 * it was last (re)issued, so a resend cooldown can be enforced.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.expiry-minutes:5}")
    private long expiryMinutes;

    @Value("${app.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** Generates and stores a fresh 6-digit OTP for the given key, enforcing the resend cooldown. */
    public String generate(String key) {
        OtpEntry existing = store.get(key);
        if (existing != null) {
            long elapsed = Duration.between(existing.issuedAt, Instant.now()).getSeconds();
            if (elapsed < resendCooldownSeconds) {
                long wait = resendCooldownSeconds - elapsed;
                throw new OtpException("Please wait " + wait + "s before requesting another OTP.");
            }
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        OtpEntry entry = new OtpEntry(passwordEncoder.encode(otp), now, now.plus(Duration.ofMinutes(expiryMinutes)));
        store.put(key, entry);
        log.info("OTP generated for key={} (expires in {} min)", maskKey(key), expiryMinutes);
        return otp;
    }

    /** Validates the supplied code against the stored OTP for this key. Single-use: removed on success. */
    public void verify(String key, String code) {
        OtpEntry entry = store.get(key);
        if (entry == null) {
            throw new OtpException("No active OTP found for this request. Please request a new code.");
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key);
            throw new OtpException("Your OTP has expired. Please request a new code.");
        }
        if (entry.attempts >= maxAttempts) {
            store.remove(key);
            throw new OtpException("Too many incorrect attempts. Please request a new code.");
        }
        if (code == null || !passwordEncoder.matches(code.trim(), entry.otpHash)) {
            entry.attempts++;
            int remaining = maxAttempts - entry.attempts;
            if (remaining <= 0) {
                store.remove(key);
                throw new OtpException("Too many incorrect attempts. Please request a new code.");
            }
            throw new OtpException("Incorrect code. " + remaining + " attempt(s) remaining.");
        }
        // Correct + single-use: invalidate immediately so it can't be replayed.
        store.remove(key);
    }

    /** Seconds remaining before a resend is allowed for this key (0 if none pending or cooldown elapsed). */
    public long secondsUntilResendAllowed(String key) {
        OtpEntry entry = store.get(key);
        if (entry == null) return 0;
        long elapsed = Duration.between(entry.issuedAt, Instant.now()).getSeconds();
        return Math.max(0, resendCooldownSeconds - elapsed);
    }

    public void invalidate(String key) {
        store.remove(key);
    }

    public long expiryMinutes() {
        return expiryMinutes;
    }

    private String maskKey(String key) {
        int at = key.indexOf('@');
        if (at <= 1) return key;
        return key.charAt(0) + "***" + key.substring(at);
    }

    private static final class OtpEntry {
        final String otpHash;
        final Instant issuedAt;
        final Instant expiresAt;
        volatile int attempts = 0;

        OtpEntry(String otpHash, Instant issuedAt, Instant expiresAt) {
            this.otpHash = otpHash;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }
}
