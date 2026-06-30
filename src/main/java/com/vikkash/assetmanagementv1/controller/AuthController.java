package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.ChangePasswordRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeLoginRequest;
import com.vikkash.assetmanagementv1.dto.ForgotPasswordRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.dto.MessageResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.dto.ResetOtpVerifyResponse;
import com.vikkash.assetmanagementv1.dto.ResetPasswordRequest;
import com.vikkash.assetmanagementv1.dto.VerifyResetOtpRequest;
import com.vikkash.assetmanagementv1.service.AdminService;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication surface consumed by the React Login page.
 * All endpoints here are permitted without a token (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminService adminService;
    private final EmployeeService employeeService;

    public AuthController(AdminService adminService, EmployeeService employeeService) {
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<LoginResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminService.login(request));
    }

    @PostMapping("/employee/login")
    public ResponseEntity<LoginResponse> employeeLogin(@Valid @RequestBody EmployeeLoginRequest request) {
        return ResponseEntity.ok(employeeService.login(request));
    }

    /**
     * Used right after a first-time login when mustChangePassword=true, and
     * also available any time from the "Change Password" screen.
     */
    @PostMapping("/employee/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        employeeService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    // ── Admin Forgot Password (Email OTP) ────────────────────────────────

    /** Step 1: admin submits their registered email; a 6-digit OTP is emailed to it. */
    @PostMapping("/admin/forgot-password")
    public ResponseEntity<OtpRequestResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(adminService.requestPasswordReset(request.getEmail()));
    }

    /** Step 2: admin submits the OTP; on success a short-lived reset token is returned. */
    @PostMapping("/admin/verify-reset-otp")
    public ResponseEntity<ResetOtpVerifyResponse> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        String resetToken = adminService.verifyPasswordResetOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(new ResetOtpVerifyResponse("Code verified successfully.", resetToken));
    }

    /** Step 3: admin sets a new password using the reset token from step 2. */
    @PostMapping("/admin/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetPassword(request.getResetToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Your password has been reset successfully. Please sign in."));
    }
}
