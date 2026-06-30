package com.vikkash.assetmanagementv1.dto;

/** Returned after a successful password-reset OTP verification: the token for the final reset step. */
public class ResetOtpVerifyResponse {
    private String message;
    private String resetToken;

    public ResetOtpVerifyResponse() {}

    public ResetOtpVerifyResponse(String message, String resetToken) {
        this.message = message;
        this.resetToken = resetToken;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
}
