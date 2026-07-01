package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.MessageResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
     * Body: { "currentPassword": "...", "newPassword": "..." }
     */
    @PostMapping("/change-password/request-otp")
    public ResponseEntity<OtpRequestResponse> requestOtp(
            @AuthenticationPrincipal String username,
            @RequestBody Map<String, String> body) {

        OtpRequestResponse response = adminService.requestChangePasswordOtp(
                username,
                body.get("currentPassword"),
                body.get("newPassword")
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/admin/change-password/confirm
     * Body: { "currentPassword": "...", "newPassword": "...", "otp": "123456" }
     */
    @PostMapping("/change-password/confirm")
    public ResponseEntity<MessageResponse> confirmChange(
            @AuthenticationPrincipal String username,
            @RequestBody Map<String, String> body) {

        adminService.changePassword(
                username,
                body.get("currentPassword"),
                body.get("newPassword"),
                body.get("otp")
        );
        return ResponseEntity.ok(new MessageResponse("Password changed successfully."));
    }
}
