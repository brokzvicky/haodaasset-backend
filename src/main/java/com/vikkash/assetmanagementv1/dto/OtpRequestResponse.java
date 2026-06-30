package com.vikkash.assetmanagementv1.dto;

/** Returned after an OTP is generated/sent: how long until resend is allowed, and OTP validity window. */
public class OtpRequestResponse {
    private String message;
    private long expiresInSeconds;
    private long resendAfterSeconds;

    public OtpRequestResponse() {}

    public OtpRequestResponse(String message, long expiresInSeconds, long resendAfterSeconds) {
        this.message = message;
        this.expiresInSeconds = expiresInSeconds;
        this.resendAfterSeconds = resendAfterSeconds;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public long getResendAfterSeconds() { return resendAfterSeconds; }
    public void setResendAfterSeconds(long resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; }
}
