package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.AdminLoginResponse;
import com.vikkash.assetmanagementv1.dto.ChangePasswordRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeLoginRequest;
import com.vikkash.assetmanagementv1.dto.ForgotPasswordRequest;
import com.vikkash.assetmanagementv1.dto.GoogleLoginRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.dto.MeResponse;
import com.vikkash.assetmanagementv1.dto.MessageResponse;
import com.vikkash.assetmanagementv1.dto.MobileOtpRequestRequest;
import com.vikkash.assetmanagementv1.dto.MobileOtpVerifyRequest;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.dto.ResendLoginOtpRequest;
import com.vikkash.assetmanagementv1.dto.ResetOtpVerifyResponse;
import com.vikkash.assetmanagementv1.dto.ResetPasswordRequest;
import com.vikkash.assetmanagementv1.dto.VerifyLoginOtpRequest;
import com.vikkash.assetmanagementv1.dto.VerifyResetOtpRequest;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.service.AdminService;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

    /**
     * Step 1 of admin login (username + password). If the admin has a
     * recovery email on file this returns a 2FA challenge rather than a
     * token — see {@link #verifyLoginOtp}. Otherwise it returns a ready
     * token directly.
     */
    @PostMapping("/admin/login")
    public ResponseEntity<AdminLoginResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminService.login(request));
    }

    /** Step 2 of admin login: verifies the emailed OTP and returns the real JWT. */
    @PostMapping("/admin/verify-login-otp")
    public ResponseEntity<LoginResponse> verifyLoginOtp(@Valid @RequestBody VerifyLoginOtpRequest request) {
        return ResponseEntity.ok(adminService.verifyLoginOtp(request.getChallengeToken(), request.getOtp()));
    }

    /** Resends the login OTP for a pending 2FA challenge. */
    @PostMapping("/admin/resend-login-otp")
    public ResponseEntity<OtpRequestResponse> resendLoginOtp(@Valid @RequestBody ResendLoginOtpRequest request) {
        return ResponseEntity.ok(adminService.resendLoginOtp(request.getChallengeToken()));
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

    // ── Google Sign-In ────────────────────────────────────────────────────
    // The frontend obtains the ID token client-side via Google Identity
    // Services (google.accounts.id) and simply forwards it here — the
    // backend verifies it (GoogleTokenVerifier) and issues our own JWT.
    // Note: unlike password login, this never auto-creates an account —
    // the Google email/identity must already match an existing admin or
    // employee row (see AdminService/EmployeeService.loginWithGoogle).
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        LoginResponse response = "admin".equalsIgnoreCase(request.getLoginAs())
                ? adminService.loginWithGoogle(request.getIdToken())
                : employeeService.loginWithGoogle(request.getIdToken());
        return ResponseEntity.ok(response);
    }

    // ── Mobile OTP login ──────────────────────────────────────────────────
    // Step 1: submit a registered mobile number, receive an OTP via SMS.
    // NOTE: SmsService currently only logs the OTP server-side — see its
    // Javadoc — until a real SMS gateway is configured.
    @PostMapping("/mobile/request-otp")
    public ResponseEntity<OtpRequestResponse> requestMobileOtp(@Valid @RequestBody MobileOtpRequestRequest request) {
        OtpRequestResponse response = "admin".equalsIgnoreCase(request.getLoginAs())
                ? adminService.requestMobileOtp(request.getMobile())
                : employeeService.requestMobileOtp(request.getMobile());
        return ResponseEntity.ok(response);
    }

    /** Step 2: submit the OTP received via SMS to receive a real JWT. */
    @PostMapping("/mobile/verify-otp")
    public ResponseEntity<LoginResponse> verifyMobileOtp(@Valid @RequestBody MobileOtpVerifyRequest request) {
        LoginResponse response = "admin".equalsIgnoreCase(request.getLoginAs())
                ? adminService.verifyMobileOtp(request.getMobile(), request.getOtp())
                : employeeService.verifyMobileOtp(request.getMobile(), request.getOtp());
        return ResponseEntity.ok(response);
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────
    // The single source of truth the frontend loads right after login (and
    // on every app reload while the token is still valid): full profile +
    // resolved permission set, so the sidebar/dashboard can render only the
    // modules this specific account is authorized for. Requires a valid
    // token — see the dedicated SecurityConfig rule that authenticates this
    // exact path before the broader "/api/auth/**" permitAll wildcard.
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null) {
            throw new InvalidCredentialsException("Not authenticated.");
        }
        String subject = String.valueOf(authentication.getPrincipal());
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        MeResponse response = isAdmin
                ? adminService.buildMeResponse(subject)
                : employeeService.buildMeResponse(subject);
        return ResponseEntity.ok(response);
    }
}
