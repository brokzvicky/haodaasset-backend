package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.dto.UnlockStatusResponse;
import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.exception.OtpException;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.security.CredentialUnlockSessionService;
import com.vikkash.assetmanagementv1.security.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gates access to sensitive Network Credential fields (login password,
 * enable password, and the copy-username/copy-password actions) behind a
 * fresh email OTP, per the logged-in admin. A successful verification
 * opens a short, time-boxed unlock window (see
 * app.security.credential-unlock-seconds) after which the UI must
 * re-verify with a brand-new OTP.
 */
@Service
public class CredentialUnlockService {

    private static final Logger log = LoggerFactory.getLogger(CredentialUnlockService.class);

    /** Namespace prefix so credential-unlock OTP keys never collide with other OTP purposes. */
    private static final String UNLOCK_NAMESPACE = "unlock:";

    private final AdminRepository adminRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final CredentialUnlockSessionService sessionService;

    public CredentialUnlockService(AdminRepository adminRepository, OtpService otpService,
                                    EmailService emailService, CredentialUnlockSessionService sessionService) {
        this.adminRepository = adminRepository;
        this.otpService = otpService;
        this.emailService = emailService;
        this.sessionService = sessionService;
    }

    @Transactional(readOnly = true)
    public OtpRequestResponse requestOtp(String adminUsername) {
        Admin admin = getAdminWithEmailOrThrow(adminUsername);
        // A fresh OTP is required every time verification is requested, even if
        // a previous unlock window is still active — re-locking immediately
        // means a stale "unlocked" state can never be reused to skip the check.
        sessionService.lock(adminUsername);
        String otp = otpService.generate(UNLOCK_NAMESPACE + adminUsername);
        emailService.sendOtpEmail(admin.getEmail(), "Network Credential Access", otp, otpService.expiryMinutes());
        log.info("Credential unlock OTP requested by admin={}", adminUsername);
        return new OtpRequestResponse(
                "A verification code has been sent to your registered email.",
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(UNLOCK_NAMESPACE + adminUsername));
    }

    public UnlockStatusResponse verifyOtp(String adminUsername, String otp) {
        otpService.verify(UNLOCK_NAMESPACE + adminUsername, otp);
        sessionService.unlock(adminUsername);
        long remaining = sessionService.secondsRemaining(adminUsername);
        log.info("Credential unlock granted to admin={} for {}s", adminUsername, remaining);
        return new UnlockStatusResponse(true, remaining);
    }

    public UnlockStatusResponse status(String adminUsername) {
        boolean unlocked = sessionService.isUnlocked(adminUsername);
        return new UnlockStatusResponse(unlocked, unlocked ? sessionService.secondsRemaining(adminUsername) : 0);
    }

    /** Throws if the admin hasn't verified an OTP within the current unlock window. Call before any reveal/decrypt. */
    public void assertUnlocked(String adminUsername) {
        if (!sessionService.isUnlocked(adminUsername)) {
            throw new OtpException("Email verification required to view this credential. Please verify with the OTP sent to your email.");
        }
    }

    public void lock(String adminUsername) {
        sessionService.lock(adminUsername);
    }

    private Admin getAdminWithEmailOrThrow(String adminUsername) {
        Admin admin = adminRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new OtpException("Admin account not found."));
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            throw new OtpException(
                    "No recovery email is configured for this admin account. Set one before unlocking credentials.");
        }
        return admin;
    }
}
