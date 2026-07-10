package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.EmployeeAssetEmailLogResponse;
import com.vikkash.assetmanagementv1.dto.EmployeeAssetsBundleResponse;
import com.vikkash.assetmanagementv1.dto.SendBulkAssetEmailRequest;
import com.vikkash.assetmanagementv1.dto.SendBulkAssetEmailResponse;
import com.vikkash.assetmanagementv1.service.EmployeeAssetEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the enterprise "Send Asset Email" admin page:
 *   1. Admin searches for an employee (Employee ID / Name / Email).
 *   2. Admin reviews that employee's currently assigned assets.
 *   3. Admin picks which assets to include and sends one email covering all of them.
 *   4. Every attempt is recorded on the "Asset Email Logs" page, with a Resend action.
 *
 * All routes are under /api/admin/** which requires ROLE_ADMIN (SecurityConfig).
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource().
 */
@RestController
@RequestMapping("/api/admin/asset-email")
public class AssetEmailBulkController {

    private final EmployeeAssetEmailService employeeAssetEmailService;

    public AssetEmailBulkController(EmployeeAssetEmailService employeeAssetEmailService) {
        this.employeeAssetEmailService = employeeAssetEmailService;
    }

    /**
     * GET /api/admin/asset-email/employee/{employeeId}
     * Employee directory details plus every asset currently assigned to
     * them, fetched together for the page's detail panel + assets table.
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeAssetsBundleResponse> getEmployeeWithAssets(@PathVariable String employeeId) {
        return ResponseEntity.ok(employeeAssetEmailService.getEmployeeWithAssets(employeeId));
    }

    /**
     * POST /api/admin/asset-email/send
     * Sends one email covering the admin-selected assets to the given
     * employee. The admin identity is taken from the JWT subject, never
     * from the request body.
     */
    @PostMapping("/send")
    public ResponseEntity<SendBulkAssetEmailResponse> sendBulkAssetEmail(@Valid @RequestBody SendBulkAssetEmailRequest request,
                                                                          Authentication authentication) {
        String sentBy = authentication != null ? authentication.getName() : "unknown";
        EmployeeAssetEmailLogResponse logEntry =
                employeeAssetEmailService.sendBulkAssetEmail(request.getEmployeeId(), request.getAssetIds(), sentBy);
        return ResponseEntity.ok(new SendBulkAssetEmailResponse(logEntry, "Asset email sent successfully."));
    }

    /**
     * POST /api/admin/asset-email/resend/{logId}
     * Resends a previously logged bulk email, re-resolving current asset
     * data first. Creates a new log row rather than overwriting the original.
     */
    @PostMapping("/resend/{logId}")
    public ResponseEntity<SendBulkAssetEmailResponse> resend(@PathVariable Long logId, Authentication authentication) {
        String sentBy = authentication != null ? authentication.getName() : "unknown";
        EmployeeAssetEmailLogResponse logEntry = employeeAssetEmailService.resend(logId, sentBy);
        return ResponseEntity.ok(new SendBulkAssetEmailResponse(logEntry, "Asset email resent successfully."));
    }

    /** GET /api/admin/asset-email/logs — powers the "Asset Email Logs" page. */
    @GetMapping("/logs")
    public List<EmployeeAssetEmailLogResponse> getEmailLogs() {
        return employeeAssetEmailService.getEmailLogs();
    }
}
