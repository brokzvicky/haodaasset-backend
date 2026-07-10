package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/auth/admin/verify-login-otp. */
public class VerifyLoginOtpRequest {

    @NotBlank(message = "Login session token is required")
    private String challengeToken;

    @NotBlank(message = "Verification code is required")
    private String otp;

    public VerifyLoginOtpRequest() {
    }

    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
