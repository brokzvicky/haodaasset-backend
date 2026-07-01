package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.MessageResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JWT-protected admin settings endpoints.
 * Mapped under /api/admin/** so Spring Security's ADMIN role guard applies.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final AdminService adminService;

    public AdminSettingsController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * POST /api/admin/change-password/request-otp
     *
     * Step 1: Admin submits current + new password.
     * Verifies current password is correct, then sends OTP to admin email.
     *
     * Body: { "currentPassword": "...", "newPassword": "..." }
     */
    @PostMapping("/change-password/request-otp")
    public ResponseEntity<OtpRequestResponse> requestOtp(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {

        String username       = userDetails.getUsername();
        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        OtpRequestResponse response =
                adminService.requestChangePasswordOtp(username, currentPassword, newPassword);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/change-password/confirm
     *
     * Step 2: Admin submits the OTP received by email.
     * Verifies OTP and saves the new BCrypt-hashed password.
     *
     * Body: { "currentPassword": "...", "newPassword": "...", "otp": "123456" }
     */
    @PostMapping("/change-password/confirm")
    public ResponseEntity<MessageResponse> confirmChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {

        String username        = userDetails.getUsername();
        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");
        String otp             = body.get("otp");

        adminService.changePassword(username, currentPassword, newPassword, otp);

        return ResponseEntity.ok(new MessageResponse("Password changed successfully."));
    }
}
