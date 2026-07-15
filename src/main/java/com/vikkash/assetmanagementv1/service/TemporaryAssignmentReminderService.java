package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Admin;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.repository.AdminRepository;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Watches every asset that is currently on a "Temporary" assignment and,
 * once its temporaryExpiryDate has passed, emails the admin who made the
 * assignment (falling back to the configured recovery inbox) with:
 * "The temporary assignment period has expired. Please collect the laptop back."
 *
 * Runs once a day on a schedule, and can also be triggered on demand via
 * AssetController#checkTemporaryExpirations for testing/manual use.
 * Each asset is only ever reminded once per assignment
 * (temporaryReturnReminderSent flips to "Yes" after a successful send, and
 * is reset back to "No" whenever the asset is next assigned or returned).
 */
@Service
public class TemporaryAssignmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(TemporaryAssignmentReminderService.class);

    private final AssetRepository assetRepository;
    private final AdminRepository adminRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${app.admin.recovery-email:}")
    private String fallbackAdminEmail;

    public TemporaryAssignmentReminderService(AssetRepository assetRepository,
                                               AdminRepository adminRepository,
                                               EmailService emailService,
                                               AuditLogService auditLogService) {
        this.assetRepository = assetRepository;
        this.adminRepository = adminRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    /**
     * Scheduled once a day at 09:00 server time. A daily cadence is enough
     * precision for a day-granularity expiry (e.g. "2 days"), while still
     * catching every expiry within the same day it occurs.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void scheduledCheck() {
        int sent = runCheck();
        if (sent > 0) {
            log.info("Scheduled temporary-assignment expiry check: {} reminder email(s) sent.", sent);
        }
    }

    /**
     * Scans every currently-Assigned, Temporary-type asset and sends the
     * "please collect the laptop back" reminder to whoever should get it,
     * for any whose expiry date has arrived and hasn't been notified yet.
     *
     * @return number of reminder emails successfully sent
     */
    @Transactional
    public int runCheck() {
        List<Asset> candidates = assetRepository.findByAssetStatusAndAssignmentType("Assigned", "Temporary");
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (Asset asset : candidates) {
            if ("Yes".equalsIgnoreCase(asset.getTemporaryReturnReminderSent())) {
                continue; // already notified for this assignment
            }
            if (asset.getTemporaryExpiryDate() == null || asset.getTemporaryExpiryDate().isBlank()) {
                continue;
            }

            LocalDate expiry;
            try {
                expiry = LocalDate.parse(asset.getTemporaryExpiryDate());
            } catch (Exception ex) {
                log.warn("Asset {} has an unparseable temporaryExpiryDate '{}', skipping.",
                        asset.getAssetId(), asset.getTemporaryExpiryDate());
                continue;
            }
            if (expiry.isAfter(today)) {
                continue; // not due yet
            }

            String recipient = resolveAdminEmail(asset.getAssignedByAdmin());
            if (recipient == null || recipient.isBlank()) {
                log.warn("No admin email available to notify for expired temporary assignment on asset {} " +
                        "(assignedByAdmin='{}'); configure app.admin.recovery-email as a fallback.",
                        asset.getAssetId(), asset.getAssignedByAdmin());
                continue;
            }

            try {
                EmailService.TemporaryAssignmentExpiredDetails details = new EmailService.TemporaryAssignmentExpiredDetails(
                        asset.getAssetId(),
                        asset.getLaptopName(),
                        asset.getBrand(),
                        asset.getModel(),
                        asset.getSerialNumber(),
                        asset.getEmployeeName(),
                        asset.getEmployeeId(),
                        asset.getTemporaryReason(),
                        asset.getTemporaryDurationDays(),
                        asset.getAssignedDate(),
                        asset.getTemporaryExpiryDate()
                );
                emailService.sendTemporaryAssignmentExpiredEmail(recipient, details);

                asset.setTemporaryReturnReminderSent("Yes");
                assetRepository.save(asset);

                auditLogService.record("ASSET", String.valueOf(asset.getAssetId()), "TEMP_ASSIGNMENT_EXPIRED",
                        "Temporary assignment period expired for '" + asset.getLaptopName() + "' held by "
                                + asset.getEmployeeName() + " (expired " + asset.getTemporaryExpiryDate()
                                + "); notified " + recipient);

                log.info("Temporary assignment expiry reminder sent for asset {} to {}", asset.getAssetId(), recipient);
                sent++;
            } catch (Exception ex) {
                // Don't let one failed email stop the rest of the batch.
                log.error("Failed to send temporary assignment expiry reminder for asset {}: {}",
                        asset.getAssetId(), ex.getMessage());
            }
        }

        return sent;
    }

    /** Prefers the assigning admin's own registered email; falls back to the shared recovery inbox. */
    private String resolveAdminEmail(String adminUsername) {
        if (adminUsername != null && !adminUsername.isBlank()) {
            Optional<Admin> admin = adminRepository.findByUsername(adminUsername);
            if (admin.isPresent() && admin.get().getEmail() != null && !admin.get().getEmail().isBlank()) {
                return admin.get().getEmail();
            }
        }
        return fallbackAdminEmail;
    }
}
