package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/auth/admin/resend-login-otp. */
public class ResendLoginOtpRequest {

    @NotBlank(message = "Login session token is required")
    private String challengeToken;

    public ResendLoginOtpRequest() {
    }

    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }
}
