package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.NetworkCredential;
import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.NetworkCredentialRepository;
import com.vikkash.assetmanagementv1.repository.ServiceBillingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Runs the module-based reminder scans that feed the Enterprise
 * Notification Center for everything that isn't a Haoda Pulse task:
 * asset warranty expiry, temporary-assignment asset return, service
 * billing due dates, and (where an admin has set a reminder date)
 * network credential rotation and firmware updates.
 *
 * Each scan applies the same 7/3/1-day-before + due-today + overdue
 * cadence as task reminders, deduped so a given record only produces one
 * notification of a given type per calendar day.
 */
@Service
public class EnterpriseReminderService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseReminderService.class);
    private static final Set<Long> REMINDER_DAY_MARKS = Set.of(7L, 3L, 1L, 0L);

    private final AssetRepository assetRepository;
    private final ServiceBillingRepository serviceBillingRepository;
    private final NetworkCredentialRepository networkCredentialRepository;
    private final NotificationService notificationService;

    public EnterpriseReminderService(AssetRepository assetRepository,
                                      ServiceBillingRepository serviceBillingRepository,
                                      NetworkCredentialRepository networkCredentialRepository,
                                      NotificationService notificationService) {
        this.assetRepository = assetRepository;
        this.serviceBillingRepository = serviceBillingRepository;
        this.networkCredentialRepository = networkCredentialRepository;
        this.notificationService = notificationService;
    }

    /** Runs daily at 08:00 server time. */
    @Scheduled(cron = "0 0 8 * * *")
    public void scheduledScan() {
        int warranty = scanWarrantyExpiry();
        int returns = scanAssetReturnReminders();
        int billing = scanServiceBillingDue();
        int rotation = scanNetworkCredentialRotation();
        int firmware = scanFirmwareUpdates();
        int total = warranty + returns + billing + rotation + firmware;
        if (total > 0) {
            log.info("Enterprise reminder scan: {} warranty, {} return, {} billing, {} rotation, {} firmware notification(s).",
                    warranty, returns, billing, rotation, firmware);
        }
    }

    // ── Bucketed day-mark helper shared by every scan below ─────────────

    private String bucketType(long daysLeft, String upcoming, String dueToday, String overdue) {
        if (daysLeft < 0) return overdue;
        if (daysLeft == 0) return dueToday;
        if (REMINDER_DAY_MARKS.contains(daysLeft)) return upcoming;
        return null;
    }

    @Transactional
    public int scanWarrantyExpiry() {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (Asset asset : assetRepository.findAll()) {
            if (asset.getWarrantyExpiry() == null || asset.getWarrantyExpiry().isBlank()) continue;
            LocalDate expiry;
            try { expiry = LocalDate.parse(asset.getWarrantyExpiry()); } catch (Exception ex) { continue; }

            long daysLeft = ChronoUnit.DAYS.between(today, expiry);
            String type = bucketType(daysLeft, "WARRANTY_EXPIRY", "WARRANTY_EXPIRY", "WARRANTY_EXPIRY");
            if (type == null) continue;

            String relatedId = String.valueOf(asset.getAssetId());
            if (notificationService.alreadyCreatedToday(type, "ASSET", relatedId)) continue;

            String status = daysLeft < 0 ? ("expired " + Math.abs(daysLeft) + " day(s) ago") : ("expires in " + daysLeft + " day(s)");
            String priority = daysLeft < 0 ? "Critical" : (daysLeft <= 3 ? "High" : "Normal");

            notificationService.create(type, "Asset", priority,
                    "Warranty " + (daysLeft < 0 ? "expired" : "expiring soon") + ": " + asset.getLaptopName(),
                    asset.getBrand() + " " + nullToEmpty(asset.getModel()) + " (Serial: " + asset.getSerialNumber() + ") warranty " + status + ".",
                    "ASSET", relatedId, expiry, adminOnly());
            created++;
        }
        return created;
    }

    @Transactional
    public int scanAssetReturnReminders() {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (Asset asset : assetRepository.findByAssetStatusAndAssignmentType("Assigned", "Temporary")) {
            if (asset.getTemporaryExpiryDate() == null || asset.getTemporaryExpiryDate().isBlank()) continue;
            if ("Yes".equalsIgnoreCase(asset.getReturnedStatus())) continue;
            LocalDate due;
            try { due = LocalDate.parse(asset.getTemporaryExpiryDate()); } catch (Exception ex) { continue; }

            long daysLeft = ChronoUnit.DAYS.between(today, due);
            String type = bucketType(daysLeft, "ASSET_RETURN_REMINDER", "ASSET_RETURN_REMINDER", "ASSET_RETURN_REMINDER");
            if (type == null) continue;

            String relatedId = String.valueOf(asset.getAssetId());
            if (notificationService.alreadyCreatedToday(type, "ASSET", relatedId)) continue;

            String status = daysLeft < 0 ? ("overdue by " + Math.abs(daysLeft) + " day(s)") : ("due in " + daysLeft + " day(s)");
            String priority = daysLeft < 0 ? "Critical" : (daysLeft <= 1 ? "High" : "Normal");

            notificationService.create(type, "Asset", priority,
                    "Asset return " + (daysLeft < 0 ? "overdue" : "reminder") + ": " + asset.getLaptopName(),
                    asset.getLaptopName() + " assigned to " + nullToEmpty(asset.getEmployeeName()) + " is " + status + ".",
                    "ASSET", relatedId, due, adminOnly());
            created++;
        }
        return created;
    }

    @Transactional
    public int scanServiceBillingDue() {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (ServiceBilling bill : serviceBillingRepository.findByStatus("Pending")) {
            if (bill.getDueDate() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, bill.getDueDate());
            String type = bucketType(daysLeft, "SERVICE_BILLING_DUE", "SERVICE_BILLING_DUE", "SERVICE_BILLING_DUE");
            if (type == null) continue;

            String relatedId = String.valueOf(bill.getId());
            if (notificationService.alreadyCreatedToday(type, "SERVICE_BILLING", relatedId)) continue;

            String status = daysLeft < 0 ? ("overdue by " + Math.abs(daysLeft) + " day(s)") : ("due in " + daysLeft + " day(s)");
            String priority = daysLeft < 0 ? "Critical" : (daysLeft <= 3 ? "High" : "Normal");
            String amount = bill.getTotalAmount() != null ? bill.getTotalAmount().toString()
                    : (bill.getAmount() != null ? bill.getAmount().toString() : "N/A");

            notificationService.create(type, "Billing", priority,
                    "Billing " + (daysLeft < 0 ? "overdue" : "due") + ": " + bill.getService(),
                    bill.getVendor() + " — " + bill.getService() + " (Amount: " + amount + ") is " + status + ".",
                    "SERVICE_BILLING", relatedId, bill.getDueDate(), adminOnly());
            created++;
        }
        return created;
    }

    @Transactional
    public int scanNetworkCredentialRotation() {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (NetworkCredential cred : networkCredentialRepository.findAll()) {
            if (cred.getRotationDueDate() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, cred.getRotationDueDate());
            String type = bucketType(daysLeft, "NETWORK_CREDENTIAL_ROTATION", "NETWORK_CREDENTIAL_ROTATION", "NETWORK_CREDENTIAL_ROTATION");
            if (type == null) continue;

            String relatedId = String.valueOf(cred.getId());
            if (notificationService.alreadyCreatedToday(type, "NETWORK_CREDENTIAL", relatedId)) continue;

            String status = daysLeft < 0 ? ("overdue by " + Math.abs(daysLeft) + " day(s)") : ("due in " + daysLeft + " day(s)");
            String priority = daysLeft < 0 ? "Critical" : "High";

            notificationService.create(type, "Security", priority,
                    "Credential rotation " + (daysLeft < 0 ? "overdue" : "due") + ": " + cred.getDeviceName(),
                    "Password rotation for " + cred.getDeviceName() + " (" + cred.getDeviceType() + ") is " + status + ".",
                    "NETWORK_CREDENTIAL", relatedId, cred.getRotationDueDate(), adminOnly());
            created++;
        }
        return created;
    }

    @Transactional
    public int scanFirmwareUpdates() {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (NetworkCredential cred : networkCredentialRepository.findAll()) {
            if (cred.getFirmwareDueDate() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, cred.getFirmwareDueDate());
            String type = bucketType(daysLeft, "FIRMWARE_UPDATE_REMINDER", "FIRMWARE_UPDATE_REMINDER", "FIRMWARE_UPDATE_REMINDER");
            if (type == null) continue;

            String relatedId = String.valueOf(cred.getId());
            if (notificationService.alreadyCreatedToday(type, "NETWORK_CREDENTIAL_FW", relatedId)) continue;

            String status = daysLeft < 0 ? ("overdue by " + Math.abs(daysLeft) + " day(s)") : ("due in " + daysLeft + " day(s)");
            String priority = daysLeft < 0 ? "High" : "Normal";

            notificationService.create(type, "Security", priority,
                    "Firmware update " + (daysLeft < 0 ? "overdue" : "due") + ": " + cred.getDeviceName(),
                    "Firmware update for " + cred.getDeviceName() + " (" + cred.getDeviceType() + ") is " + status + ".",
                    "NETWORK_CREDENTIAL", relatedId, cred.getFirmwareDueDate(), adminOnly());
            created++;
        }
        return created;
    }

    private NotificationService.Recipients adminOnly() {
        return new NotificationService.Recipients();
    }

    private String nullToEmpty(String s) { return s != null ? s : ""; }
}
