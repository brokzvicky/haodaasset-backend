package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.CustomNotificationRequest;
import com.vikkash.assetmanagementv1.dto.SnoozeRequest;
import com.vikkash.assetmanagementv1.entity.Notification;
import com.vikkash.assetmanagementv1.service.EnterpriseReminderService;
import com.vikkash.assetmanagementv1.service.NotificationService;
import com.vikkash.assetmanagementv1.service.PulseReminderScheduler;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Enterprise Notification Center API: unified list, unread badge, mark
 * read / snooze / complete / clear, real-time SSE stream, and an on-demand
 * scan trigger (also runs on the schedules in PulseReminderScheduler /
 * EnterpriseReminderService). Mapped under /api/admin/** so the ADMIN
 * role guard applies automatically (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/pulse/notifications")
public class PulseNotificationController {

    private final NotificationService notificationService;
    private final PulseReminderScheduler pulseReminderScheduler;
    private final EnterpriseReminderService enterpriseReminderService;

    public PulseNotificationController(NotificationService notificationService,
                                        PulseReminderScheduler pulseReminderScheduler,
                                        EnterpriseReminderService enterpriseReminderService) {
        this.notificationService = notificationService;
        this.pulseReminderScheduler = pulseReminderScheduler;
        this.enterpriseReminderService = enterpriseReminderService;
    }

    @GetMapping
    public List<Notification> getRecent() {
        return notificationService.getRecent();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return notificationService.registerEmitter();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        return ResponseEntity.ok(Map.of("markedRead", notificationService.markAllRead()));
    }

    @PutMapping("/{id}/snooze")
    public ResponseEntity<Notification> snooze(@PathVariable Long id, @RequestBody(required = false) SnoozeRequest request) {
        int minutes = (request != null && request.getMinutes() != null) ? request.getMinutes() : 60;
        return ResponseEntity.ok(notificationService.snooze(id, minutes));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Notification> complete(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markActioned(id));
    }

    @DeleteMapping("/clear-completed")
    public ResponseEntity<Map<String, Object>> clearCompleted() {
        return ResponseEntity.ok(Map.of("cleared", notificationService.clearCompleted()));
    }

    /** Manual notification, for types with no automated data source yet (Security Alert, Backup Reminder, License Expiry). */
    @PostMapping("/custom")
    public ResponseEntity<Notification> createCustom(@Valid @RequestBody CustomNotificationRequest request) {
        NotificationService.Recipients recipients = new NotificationService.Recipients();
        recipients.assigneeEmail = request.getRecipientEmail();
        Notification n = notificationService.create(
                request.getNotificationType(),
                request.getCategory() != null ? request.getCategory() : "System",
                request.getPriority() != null ? request.getPriority() : "Normal",
                request.getTitle(), request.getDescription(),
                request.getRelatedModule(), request.getRelatedRecordId(), recipients);
        return ResponseEntity.status(201).body(n);
    }

    /** Triggers all reminder scans on demand (also run daily on their own schedules). */
    @PostMapping("/run-scan")
    public ResponseEntity<Map<String, Object>> runScan() {
        int tasks = pulseReminderScheduler.scanTaskDueDates();
        int warranty = enterpriseReminderService.scanWarrantyExpiry();
        int returns = enterpriseReminderService.scanAssetReturnReminders();
        int billing = enterpriseReminderService.scanServiceBillingDue();
        int rotation = enterpriseReminderService.scanNetworkCredentialRotation();
        int firmware = enterpriseReminderService.scanFirmwareUpdates();
        return ResponseEntity.ok(Map.of(
                "taskNotifications", tasks,
                "warrantyNotifications", warranty,
                "assetReturnNotifications", returns,
                "billingNotifications", billing,
                "credentialRotationNotifications", rotation,
                "firmwareNotifications", firmware
        ));
    }
}
