package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.AdminLoginResponse;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.dto.MeResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.entity.AuthProvider;
import com.vikkash.assetmanagementv1.entity.Permission;
import com.vikkash.assetmanagementv1.entity.Role;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.exception.OtpException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.security.GoogleTokenVerifier;
import com.vikkash.assetmanagementv1.security.JwtUtil;
import com.vikkash.assetmanagementv1.security.OtpService;
import com.vikkash.assetmanagementv1.security.PasswordResetTokenService;
import com.vikkash.assetmanagementv1.security.TwoFactorTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    /** Namespace prefix so password-reset OTP keys never collide with other OTP purposes. */
    private static final String PW_RESET_NAMESPACE = "pwreset:";

    /** Separate namespace for the authenticated change-password flow in Settings. */
    private static final String PW_CHANGE_NAMESPACE = "pwchange:";

    /** Separate namespace for the admin login 2FA OTP. */
    private static final String LOGIN_2FA_NAMESPACE = "login2fa:";

    /** Separate namespace for the "Login with Mobile" OTP flow. */
    private static final String MOBILE_LOGIN_NAMESPACE = "adminmobilelogin:";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordResetTokenService resetTokenService;
    private final TwoFactorTokenService twoFactorTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final SmsService smsService;

    /**
     * Every admin login OTP is sent here rather than to the individual
     * admin's personal recovery email, so the IT Support team has central
     * visibility/control over every admin sign-in — regardless of which
     * admin account is logging in.
     */
    @Value("${app.admin.2fa-email:itsupport@haodapayments.com}")
    private String twoFactorEmail;

    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                         OtpService otpService, EmailService emailService,
                         PasswordResetTokenService resetTokenService,
                         TwoFactorTokenService twoFactorTokenService,
                         GoogleTokenVerifier googleTokenVerifier,
                         SmsService smsService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.emailService = emailService;
        this.resetTokenService = resetTokenService;
        this.twoFactorTokenService = twoFactorTokenService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.smsService = smsService;
    }

    /**
     * Step 1 of admin login: verifies username + password, then always
     * sends the 2FA OTP to the shared IT Support inbox (app.admin.2fa-email)
     * rather than the individual admin's personal email — the JWT is NOT
     * issued yet (see {@link #verifyLoginOtp}).
     */
    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String key = LOGIN_2FA_NAMESPACE + admin.getUsername();
        String otp = otpService.generate(key);
        emailService.sendOtpEmail(twoFactorEmail, "Admin Login Verification", otp, otpService.expiryMinutes());
        String challengeToken = twoFactorTokenService.issue(admin.getUsername());
        log.info("2FA OTP sent to IT Support inbox for admin login id={}", admin.getId());

        return AdminLoginResponse.challenge(
                challengeToken,
                "A verification code has been sent to " + maskEmail(twoFactorEmail) + ".",
                maskEmail(twoFactorEmail),
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(key));
    }

    /**
     * Step 2 of admin login: verifies the OTP against the challenge token
     * from step 1 and, only on success, issues the real JWT. A wrong code
     * does NOT invalidate the challenge token, so the admin can retry (up
     * to OtpService's max-attempts) without restarting the whole login.
     */
    @Transactional(readOnly = true)
    public LoginResponse verifyLoginOtp(String challengeToken, String otp) {
        String username = twoFactorTokenService.peek(challengeToken);
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Admin account not found."));

        otpService.verify(LOGIN_2FA_NAMESPACE + admin.getUsername(), otp);
        twoFactorTokenService.consume(challengeToken); // single-use once verification actually succeeds

        log.info("2FA login completed for admin id={}", admin.getId());
        return issueLoginResponse(admin);
    }

    /** Resends the login OTP (to the same IT Support inbox) for a pending 2FA challenge. */
    @Transactional(readOnly = true)
    public OtpRequestResponse resendLoginOtp(String challengeToken) {
        String username = twoFactorTokenService.peek(challengeToken);
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Admin account not found."));

        String key = LOGIN_2FA_NAMESPACE + admin.getUsername();
        String otp = otpService.generate(key);
        emailService.sendOtpEmail(twoFactorEmail, "Admin Login Verification", otp, otpService.expiryMinutes());
        log.info("2FA OTP resent to IT Support inbox for admin login id={}", admin.getId());

        return new OtpRequestResponse(
                "A new verification code has been sent to " + maskEmail(twoFactorEmail) + ".",
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(key));
    }

    private LoginResponse issueLoginResponse(Admin admin) {
        String token = jwtUtil.generateToken(admin.getUsername(), "ADMIN", permissionCodes(admin.getRoleRef()));
        LoginResponse response = LoginResponse.forAdmin(token, admin.getUsername());
        response.setEmail(admin.getEmail());
        return response;
    }

    /** Fine-grained permission codes from this admin's assigned Role (empty list if none assigned yet). */
    private List<String> permissionCodes(Role role) {
        if (role == null) return List.of();
        return role.getPermissions().stream().map(Permission::getCode).toList();
    }

    // ── Google Sign-In ───────────────────────────────────────────────────
    // Deliberately does NOT auto-provision new admin accounts: an admin
    // account is a privileged, IT-provisioned identity, so signing in with
    // Google only succeeds if that Google account's email (or a previously
    // linked googleId) already matches an existing admin row. Skips the
    // internal email-OTP 2FA challenge, since Google's own sign-in already
    // constitutes a strong second factor.
    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifier.VerifiedGoogleIdentity identity = googleTokenVerifier.verify(idToken);

        Admin admin = adminRepository.findByGoogleId(identity.googleId)
                .or(() -> adminRepository.findByEmailIgnoreCase(identity.email))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "No admin account is registered for this Google account. Ask your System Admin to add it first."));

        if (admin.getGoogleId() == null) {
            admin.setGoogleId(identity.googleId);
            if (admin.getAuthProvider() == null) admin.setAuthProvider(AuthProvider.LOCAL);
            adminRepository.save(admin);
            log.info("Linked Google identity to existing admin id={}", admin.getId());
        }

        log.info("Google Sign-In completed for admin id={}", admin.getId());
        return issueLoginResponse(admin);
    }

    // ── Mobile OTP login ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public OtpRequestResponse requestMobileOtp(String mobile) {
        Admin admin = adminRepository.findByMobile(mobile.trim())
                .orElseThrow(() -> new InvalidCredentialsException("No admin account is registered with this mobile number."));

        String key = MOBILE_LOGIN_NAMESPACE + admin.getMobile();
        String otp = otpService.generate(key);
        smsService.sendOtp(admin.getMobile(), otp, otpService.expiryMinutes());
        log.info("Mobile login OTP sent for admin id={}", admin.getId());
        return new OtpRequestResponse(
                "A verification code has been sent to your registered mobile number.",
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(key));
    }

    @Transactional
    public LoginResponse verifyMobileOtp(String mobile, String otp) {
        Admin admin = adminRepository.findByMobile(mobile.trim())
                .orElseThrow(() -> new InvalidCredentialsException("No admin account is registered with this mobile number."));

        otpService.verify(MOBILE_LOGIN_NAMESPACE + admin.getMobile(), otp);
        log.info("Mobile OTP login completed for admin id={}", admin.getId());
        return issueLoginResponse(admin);
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MeResponse buildMeResponse(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found."));

        MeResponse me = new MeResponse();
        me.setRole("ADMIN");
        Role role = admin.getRoleRef();
        if (role != null) {
            me.setRoleName(role.getName());
            me.setRoleLabel(role.getLabel());
        }
        me.setName(admin.getName() != null ? admin.getName() : admin.getUsername());
        me.setEmail(admin.getEmail());
        me.setMobile(admin.getMobile());
        me.setDepartment(admin.getDepartment());
        me.setDesignation(admin.getDesignation());
        me.setBranch(admin.getBranch());
        me.setProfilePhotoUrl(admin.getProfilePhotoUrl());
        me.setMustChangePassword(false);
        me.setPermissions(permissionCodes(role));
        return me;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    // ── Forgot Password (Email OTP) ──────────────────────────────────────

    /** Step 1: verifies the email belongs to an admin, generates a fresh OTP, and emails it. */
    @Transactional(readOnly = true)
    public OtpRequestResponse requestPasswordReset(String email) {
        Admin admin = getByEmailOrThrow(email);
        String key = PW_RESET_NAMESPACE + admin.getEmail().toLowerCase();
        String otp = otpService.generate(key);
        emailService.sendOtpEmail(admin.getEmail(), "Admin Password Reset", otp, otpService.expiryMinutes());
        log.info("Password reset OTP requested for admin id={}", admin.getId());
        return new OtpRequestResponse(
                "A verification code has been sent to " + admin.getEmail() + ".",
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(key));
    }

    /** Step 2: verifies the OTP and issues a short-lived token used to actually change the password. */
    @Transactional(readOnly = true)
    public String verifyPasswordResetOtp(String email, String otp) {
        Admin admin = getByEmailOrThrow(email);
        otpService.verify(PW_RESET_NAMESPACE + admin.getEmail().toLowerCase(), otp);
        return resetTokenService.issue(admin.getEmail());
    }

    /** Step 3: consumes the reset token and sets the new BCrypt-hashed password. */
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        String email = resetTokenService.consume(resetToken);
        Admin admin = getByEmailOrThrow(email);
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
        log.info("Password reset completed for admin id={}", admin.getId());
    }

    // ── Authenticated Change Password (Settings page) ────────────────────

    /**
     * Step 1: admin is already logged in. They submit their current password
     * and desired new password. We verify the current password is correct,
     * then send an OTP to their registered email before actually saving.
     */
    @Transactional(readOnly = true)
    public OtpRequestResponse requestChangePasswordOtp(String username, String currentPassword,
                                                        String newPassword) {
        Admin admin = adminRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("Admin account not found."));

        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
            String key = PW_CHANGE_NAMESPACE + admin.getUsername();
            String otp = otpService.generate(key);
            emailService.sendOtpEmail(admin.getEmail(), "Admin Password Change", otp, otpService.expiryMinutes());
            log.info("Change-password OTP sent to admin id={}", admin.getId());
            return new OtpRequestResponse(
                    "A verification code has been sent to " + admin.getEmail() + ". Enter it below to confirm the change.",
                    otpService.expiryMinutes() * 60,
                    otpService.secondsUntilResendAllowed(key));
        }
        throw new OtpException("No email address is registered for this admin account. Please contact support.");
    }

    /**
     * Step 2: admin submits the OTP. On success the new password is saved.
     */
    @Transactional
    public void changePassword(String username, String currentPassword,
                               String newPassword, String otp) {
        Admin admin = adminRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("Admin account not found."));

        if (!passwordEncoder.matches(currentPassword, admin.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }
        otpService.verify(PW_CHANGE_NAMESPACE + admin.getUsername(), otp);
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
        log.info("Password changed via Settings for admin id={}", admin.getId());
    }

    private Admin getByEmailOrThrow(String email) {
        if (email == null || email.isBlank()) {
            throw new OtpException("Email is required.");
        }
        return adminRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No admin account is registered with this email address."));
    }
}
