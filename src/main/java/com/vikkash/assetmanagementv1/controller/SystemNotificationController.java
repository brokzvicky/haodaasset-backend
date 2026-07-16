package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.entity.SystemNotification;
import com.vikkash.assetmanagementv1.service.NotificationGeneratorService;
import com.vikkash.assetmanagementv1.service.SystemNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Notification System: system-generated alerts (warranty expiring,
 * maintenance due, etc.) shown in the admin notification bell. Mapped
 * under /api/admin/** so the ADMIN role guard applies automatically
 * (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class SystemNotificationController {

    private final SystemNotificationService notificationService;
    private final NotificationGeneratorService generatorService;

    public SystemNotificationController(SystemNotificationService notificationService,
                                         NotificationGeneratorService generatorService) {
        this.notificationService = notificationService;
        this.generatorService = generatorService;
    }

    @GetMapping
    public List<SystemNotification> getRecent() {
        return notificationService.getRecent();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<SystemNotification> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        int count = notificationService.markAllRead();
        return ResponseEntity.ok(Map.of("markedRead", count));
    }

    /** Triggers the warranty/maintenance scan on demand (also runs daily at 08:30). */
    @PostMapping("/run-scan")
    public ResponseEntity<Map<String, Object>> runScan() {
        int warranty = generatorService.scanWarrantyExpirations();
        int maintenance = generatorService.scanMaintenanceDue();
        return ResponseEntity.ok(Map.of("warrantyNotifications", warranty, "maintenanceNotifications", maintenance));
    }
}
