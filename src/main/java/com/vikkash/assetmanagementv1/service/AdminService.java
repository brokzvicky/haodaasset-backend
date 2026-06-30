package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.exception.OtpException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.security.JwtUtil;
import com.vikkash.assetmanagementv1.security.OtpService;
import com.vikkash.assetmanagementv1.security.PasswordResetTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    /** Namespace prefix so password-reset OTP keys never collide with other OTP purposes. */
    private static final String PW_RESET_NAMESPACE = "pwreset:";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordResetTokenService resetTokenService;

    public AdminService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                         OtpService otpService, EmailService emailService,
                         PasswordResetTokenService resetTokenService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.emailService = emailService;
        this.resetTokenService = resetTokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(admin.getUsername(), "ADMIN");
        LoginResponse response = LoginResponse.forAdmin(token, admin.getUsername());
        response.setEmail(admin.getEmail());
        return response;
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

    private Admin getByEmailOrThrow(String email) {
        if (email == null || email.isBlank()) {
            throw new OtpException("Email is required.");
        }
        return adminRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No admin account is registered with this email address."));
    }
}
