package com.vikkash.assetmanagementv1.exception;

/**
 * Thrown for any OTP lifecycle failure: missing/expired OTP, wrong code,
 * too many attempts, resend requested before the cooldown elapsed, or an
 * expired/invalid reset/unlock session token. Always safe to surface the
 * message directly to the client.
 */
public class OtpException extends RuntimeException {
    public OtpException(String message) {
        super(message);
    }
}
