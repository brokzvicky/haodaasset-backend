package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.entity.AuditLog;
import com.vikkash.assetmanagementv1.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mapped under /api/admin/** so Spring Security's ADMIN role guard
 * (SecurityConfig) applies automatically — no separate security rule needed.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * GET /api/admin/audit-logs?entityType=ASSET
     * Returns the 300 most recent activity entries, newest first. Omit
     * entityType (or pass "All") for the full cross-entity feed.
     */
    @GetMapping
    public List<AuditLog> recent(@RequestParam(required = false) String entityType) {
        return auditLogService.getRecent(entityType);
    }
}
