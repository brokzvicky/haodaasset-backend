package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.service.MaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Maintenance Module: schedule, track, and complete preventive/corrective
 * maintenance work against assets. Mapped under /api/admin/** so the ADMIN
 * role guard applies automatically (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public List<MaintenanceRecord> getAll() {
        return maintenanceService.getAll();
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        return maintenanceService.getStats();
    }

    @GetMapping("/asset/{assetId}")
    public List<MaintenanceRecord> getForAsset(@PathVariable Long assetId) {
        return maintenanceService.getForAsset(assetId);
    }

    @PostMapping
    public ResponseEntity<MaintenanceRecord> create(@RequestBody MaintenanceRecord record, Authentication authentication) {
        String createdBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.status(201).body(maintenanceService.create(record, createdBy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceRecord> update(@PathVariable Long id, @RequestBody MaintenanceRecord record,
                                                     Authentication authentication) {
        String updatedBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.ok(maintenanceService.update(id, record, updatedBy));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, Authentication authentication) {
        String deletedBy = authentication != null ? authentication.getName() : "unknown";
        maintenanceService.delete(id, deletedBy);
        return ResponseEntity.ok(Map.of("message", "Maintenance record deleted successfully"));
    }
}
