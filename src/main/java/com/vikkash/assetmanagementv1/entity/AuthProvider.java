package com.vikkash.assetmanagementv1.entity;

/**
 * How this account authenticates. LOCAL = employeeId/username + password
 * (and optionally mobile OTP, using the same local credential record).
 * GOOGLE = Google Sign-In only — {@code googleId} is populated and
 * {@code password} may be a random unusable placeholder for accounts that
 * were provisioned via Google and have never set a local password.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
