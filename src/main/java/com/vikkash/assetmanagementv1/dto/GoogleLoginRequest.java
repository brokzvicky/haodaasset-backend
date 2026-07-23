package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST /api/auth/google. {@code idToken} is the credential JWT the
 * Google Identity Services JS library returns to the frontend after the
 * user picks a Google account — the frontend never talks to Google's OAuth
 * token endpoint directly, it just forwards this token here for the backend
 * to verify (see GoogleTokenVerifier).
 */
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    /** "admin" or "employee" — which login surface this attempt is for, same convention as the existing tabs. */
    @NotBlank(message = "loginAs is required")
    private String loginAs;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getLoginAs() { return loginAs; }
    public void setLoginAs(String loginAs) { this.loginAs = loginAs; }
}
