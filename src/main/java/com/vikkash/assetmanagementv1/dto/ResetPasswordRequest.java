package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Step 3 of Admin Forgot Password: set the new password using the short-lived reset token. */
public class ResetPasswordRequest {

    @NotBlank(message = "Reset session is missing or invalid")
    private String resetToken;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String newPassword;

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
