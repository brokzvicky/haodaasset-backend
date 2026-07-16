package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.entity.SystemNotification;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.MaintenanceRecordRepository;
import com.vikkash.assetmanagementv1.repository.SystemNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Runs the daily scans that power the Notification System bell:
 *  - Warranty Tracking: assets whose warrantyExpiry falls within the next
 *    30 days (or has already passed) get a "WARRANTY_EXPIRING" notification.
 *  - Maintenance Module: scheduled maintenance whose date falls within the
 *    next 7 days gets a "MAINTENANCE_DUE" notification.
 *
 * Each notification is deduplicated per entity+type so a re-run (or the
 * daily schedule firing again) doesn't create a fresh row every day for the
 * same still-pending issue — it only creates one if none already exists
 * from the last 7 days.
 */
@Service
public class NotificationGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(NotificationGeneratorService.class);
    private static final int WARRANTY_LOOKAHEAD_DAYS = 30;
    private static final int MAINTENANCE_LOOKAHEAD_DAYS = 7;

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final SystemNotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${app.admin.recovery-email:}")
    private String adminEmail;

    public NotificationGeneratorService(AssetRepository assetRepository,
                                         MaintenanceRecordRepository maintenanceRepository,
                                         SystemNotificationRepository notificationRepository,
                                         EmailService emailService) {
        this.assetRepository = assetRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    /** Runs once a day at 08:30 server time, ahead of the temp-assignment check at 09:00. */
    @Scheduled(cron = "0 30 8 * * *")
    public void scheduledScan() {
        int warranty = scanWarrantyExpirations();
        int maintenance = scanMaintenanceDue();
        if (warranty + maintenance > 0) {
            log.info("Notification scan: {} warranty, {} maintenance notification(s) created.", warranty, maintenance);
        }
    }

    @Transactional
    public int scanWarrantyExpirations() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(WARRANTY_LOOKAHEAD_DAYS);
        int created = 0;

        for (Asset asset : assetRepository.findAll()) {
            if (asset.getWarrantyExpiry() == null || asset.getWarrantyExpiry().isBlank()) continue;
            LocalDate expiry;
            try {
                expiry = LocalDate.parse(asset.getWarrantyExpiry());
            } catch (Exception ex) {
                continue;
            }
            if (expiry.isAfter(horizon)) continue; // not due for a reminder yet

            String assetIdStr = String.valueOf(asset.getAssetId());
            boolean alreadyNotified = existsRecentNotification("WARRANTY_EXPIRING", "ASSET", assetIdStr);
            if (alreadyNotified) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, expiry);
            String severity = daysLeft < 0 ? "critical" : (daysLeft <= 7 ? "warning" : "info");
            String status = daysLeft < 0 ? ("expired " + Math.abs(daysLeft) + " day(s) ago")
                    : ("expires in " + daysLeft + " day(s)");

            SystemNotification notif = new SystemNotification();
            notif.setType("WARRANTY_EXPIRING");
            notif.setSeverity(severity);
            notif.setTitle("Warranty " + (daysLeft < 0 ? "expired" : "expiring soon") + ": " + asset.getLaptopName());
            notif.setMessage(asset.getBrand() + " " + (asset.getModel() != null ? asset.getModel() : "")
                    + " (Serial: " + asset.getSerialNumber() + ") warranty " + status + ".");
            notif.setRelatedType("ASSET");
            notif.setRelatedId(assetIdStr);
            notificationRepository.save(notif);
            created++;

            if (adminEmail != null && !adminEmail.isBlank()) {
                try {
                    emailService.sendSimpleNotificationEmail(adminEmail,
                            "Warranty Alert: " + asset.getLaptopName(),
                            "Asset warranty " + (daysLeft < 0 ? "has expired" : "is expiring soon"),
                            "<p><b>" + asset.getLaptopName() + "</b> (" + asset.getBrand() + " "
                                    + (asset.getModel() != null ? asset.getModel() : "") + ", Serial: "
                                    + asset.getSerialNumber() + ") warranty " + status + ".</p>");
                } catch (Exception ex) {
                    log.warn("Failed to email warranty alert for asset {}: {}", asset.getAssetId(), ex.getMessage());
                }
            }
        }
        return created;
    }

    @Transactional
    public int scanMaintenanceDue() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(MAINTENANCE_LOOKAHEAD_DAYS);
        int created = 0;

        List<MaintenanceRecord> scheduled = maintenanceRepository.findByStatusOrderByScheduledDateAsc("Scheduled");
        for (MaintenanceRecord record : scheduled) {
            if (record.getScheduledDate() == null || record.getScheduledDate().isBlank()) continue;
            LocalDate due;
            try {
                due = LocalDate.parse(record.getScheduledDate());
            } catch (Exception ex) {
                continue;
            }
            if (due.isAfter(horizon)) continue;

            String recordIdStr = String.valueOf(record.getId());
            if (existsRecentNotification("MAINTENANCE_DUE", "MAINTENANCE", recordIdStr)) continue;

            Asset asset = assetRepository.findById(record.getAssetId()).orElse(null);
            String assetName = asset != null ? asset.getLaptopName() : ("Asset #" + record.getAssetId());
            long daysLeft = ChronoUnit.DAYS.between(today, due);

            SystemNotification notif = new SystemNotification();
            notif.setType("MAINTENANCE_DUE");
            notif.setSeverity(daysLeft < 0 ? "critical" : "warning");
            notif.setTitle("Maintenance due: " + assetName);
            notif.setMessage(record.getMaintenanceType() + " maintenance for '" + assetName + "' "
                    + (daysLeft < 0 ? "is overdue" : "is due in " + daysLeft + " day(s)") + ".");
            notif.setRelatedType("MAINTENANCE");
            notif.setRelatedId(recordIdStr);
            notificationRepository.save(notif);
            created++;
        }
        return created;
    }

    private boolean existsRecentNotification(String type, String relatedType, String relatedId) {
        Optional<SystemNotification> existing = notificationRepository
                .findFirstByTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(type, relatedType, relatedId);
        return existing.map(n -> n.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(7))).orElse(false);
    }
}
