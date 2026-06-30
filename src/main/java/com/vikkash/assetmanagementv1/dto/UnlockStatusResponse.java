package com.vikkash.assetmanagementv1.dto;

/** Reports whether the current admin's sensitive-credential unlock window is currently active. */
public class UnlockStatusResponse {
    private boolean unlocked;
    private long secondsRemaining;

    public UnlockStatusResponse() {}

    public UnlockStatusResponse(boolean unlocked, long secondsRemaining) {
        this.unlocked = unlocked;
        this.secondsRemaining = secondsRemaining;
    }

    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

    public long getSecondsRemaining() { return secondsRemaining; }
    public void setSecondsRemaining(long secondsRemaining) { this.secondsRemaining = secondsRemaining; }
}
