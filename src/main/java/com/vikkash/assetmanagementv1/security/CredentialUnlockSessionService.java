package com.vikkash.assetmanagementv1.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks, per logged-in admin username, whether they currently have an
 * active "sensitive network credentials unlocked" window after a
 * successful OTP verification. Purely in-memory and time-boxed — never
 * persisted, and automatically expires.
 */
@Service
public class CredentialUnlockSessionService {

    private final Map<String, Instant> unlockedUntil = new ConcurrentHashMap<>();

    @Value("${app.security.credential-unlock-seconds:60}")
    private long unlockSeconds;

    public void unlock(String adminUsername) {
        unlockedUntil.put(adminUsername, Instant.now().plusSeconds(unlockSeconds));
    }

    public boolean isUnlocked(String adminUsername) {
        Instant expiry = unlockedUntil.get(adminUsername);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            unlockedUntil.remove(adminUsername);
            return false;
        }
        return true;
    }

    public long secondsRemaining(String adminUsername) {
        Instant expiry = unlockedUntil.get(adminUsername);
        if (expiry == null) return 0;
        long remaining = Duration.between(Instant.now(), expiry).getSeconds();
        return Math.max(remaining, 0);
    }

    public void lock(String adminUsername) {
        unlockedUntil.remove(adminUsername);
    }

    public long unlockWindowSeconds() {
        return unlockSeconds;
    }
}
