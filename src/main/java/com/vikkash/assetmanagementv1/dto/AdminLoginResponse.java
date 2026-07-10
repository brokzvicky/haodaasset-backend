package com.vikkash.assetmanagementv1.dto;

/**
 * Response for POST /api/auth/admin/login.
 *
 * Two shapes, discriminated by twoFactorRequired:
 *   - twoFactorRequired = true  → challengeToken/maskedEmail/expiresInSeconds/resendAfterSeconds
 *                                  are populated; the React app shows the OTP-entry step and
 *                                  must call /verify-login-otp to actually receive a JWT.
 *                                  Currently this is always the case — every admin login OTP
 *                                  is sent to the shared IT Support inbox (app.admin.2fa-email).
 *   - twoFactorRequired = false → login is populated with a ready-to-use JWT. Reserved for a
 *                                  future "skip 2FA" path (e.g. a trusted-device toggle); not
 *                                  currently produced by AdminService.
 */
public class AdminLoginResponse {

    private boolean twoFactorRequired;
    private String message;

    // Populated only when twoFactorRequired = true
    private String challengeToken;
    private String maskedEmail;
    private long expiresInSeconds;
    private long resendAfterSeconds;

    // Populated only when twoFactorRequired = false
    private LoginResponse login;

    public AdminLoginResponse() {
    }

    public static AdminLoginResponse direct(LoginResponse login) {
        AdminLoginResponse r = new AdminLoginResponse();
        r.twoFactorRequired = false;
        r.login = login;
        r.message = "Login successful.";
        return r;
    }

    public static AdminLoginResponse challenge(String challengeToken, String message, String maskedEmail,
                                                long expiresInSeconds, long resendAfterSeconds) {
        AdminLoginResponse r = new AdminLoginResponse();
        r.twoFactorRequired = true;
        r.challengeToken = challengeToken;
        r.message = message;
        r.maskedEmail = maskedEmail;
        r.expiresInSeconds = expiresInSeconds;
        r.resendAfterSeconds = resendAfterSeconds;
        return r;
    }

    public boolean isTwoFactorRequired() { return twoFactorRequired; }
    public void setTwoFactorRequired(boolean twoFactorRequired) { this.twoFactorRequired = twoFactorRequired; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }

    public String getMaskedEmail() { return maskedEmail; }
    public void setMaskedEmail(String maskedEmail) { this.maskedEmail = maskedEmail; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public long getResendAfterSeconds() { return resendAfterSeconds; }
    public void setResendAfterSeconds(long resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; }

    public LoginResponse getLogin() { return login; }
    public void setLogin(LoginResponse login) { this.login = login; }
}
