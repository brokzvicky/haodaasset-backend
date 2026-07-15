package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AssetEmailLogResponse;
import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.dto.OrphanedAssetDTO;
import com.vikkash.assetmanagementv1.dto.RepairResultDTO;
import com.vikkash.assetmanagementv1.dto.SendAssetEmailResponse;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.service.AssetEmailService;
import com.vikkash.assetmanagementv1.service.AssetService;
import com.vikkash.assetmanagementv1.service.TemporaryAssignmentReminderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for asset inventory management.
 * Requires ROLE_ADMIN (enforced in SecurityConfig for /assets/**).
 * All business logic is delegated to AssetService.
 *
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource() so
 * that both the local dev origin and the deployed frontend origin are
 * honored consistently — a controller-level @CrossOrigin here would
 * override/conflict with that and silently break CORS for one of them.
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetEmailService assetEmailService;
    private final TemporaryAssignmentReminderService temporaryAssignmentReminderService;

    public AssetController(AssetService assetService, AssetEmailService assetEmailService,
                            TemporaryAssignmentReminderService temporaryAssignmentReminderService) {
        this.assetService = assetService;
        this.assetEmailService = assetEmailService;
        this.temporaryAssignmentReminderService = temporaryAssignmentReminderService;
    }

    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    /**
     * Diagnostic, read-only: lists every "Assigned" asset whose employeeId
     * link is broken (missing, or pointing at an employeeId that doesn't
     * exist). These are the assets that show a name on this page but won't
     * show up under that employee's "View Assets" panel on the Employees
     * page. Makes no data changes — just a report to find them.
     */
    @GetMapping("/orphaned-assignments")
    public List<OrphanedAssetDTO> getOrphanedAssignments() {
        return assetService.findOrphanedAssignments();
    }

    /**
     * Repairs every asset found by getOrphanedAssignments(): clears its
     * broken assignment fields and resets it to Available, so it can be
     * correctly re-assigned via the normal Assign Asset flow. Does not
     * guess or auto-assign an employee. Returns a summary of what changed.
     */
    @PutMapping("/repair-orphaned-assignments")
    public List<RepairResultDTO> repairOrphanedAssignments() {
        return assetService.repairOrphanedAssignments();
    }

    @GetMapping("/available")
    public List<Asset> getAvailableAssets() {
        return assetService.getAvailableAssets();
    }

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard() {
        return assetService.getDashboardStats();
    }

    @GetMapping("/employee/{name}")
    public List<Asset> getAssetsByEmployee(@PathVariable String name) {
        return assetService.getByEmployee(name);
    }

    @GetMapping("/serial/{serialNumber}")
    public Asset getAssetBySerialNumber(@PathVariable String serialNumber) {
        return assetService.getBySerialNumber(serialNumber);
    }

    @PostMapping
    public ResponseEntity<Asset> saveAsset(@RequestBody Asset asset) {
        return ResponseEntity.status(201).body(assetService.createAsset(asset));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Asset> updateAsset(@PathVariable Long id, @RequestBody Asset updatedAsset) {
        return ResponseEntity.ok(assetService.updateAsset(id, updatedAsset));
    }

    @PutMapping("/assign/{id}")
    public ResponseEntity<Asset> assignAsset(@PathVariable Long id,
                                              @Valid @RequestBody AssignAssetRequest request,
                                              Authentication authentication) {
        String assignedByAdmin = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(assetService.assignAsset(id, request, assignedByAdmin));
    }

    /**
     * Manually runs the "temporary assignment expired" scan (normally run on
     * a daily schedule — see TemporaryAssignmentReminderService). Handy for
     * an admin who wants to trigger the check on demand rather than waiting
     * for the next scheduled run.
     */
    @PostMapping("/check-temporary-expirations")
    public ResponseEntity<Map<String, Object>> checkTemporaryExpirations() {
        int sent = temporaryAssignmentReminderService.runCheck();
        return ResponseEntity.ok(Map.of("remindersSent", sent));
    }

    @PutMapping("/return/{id}")
    public ResponseEntity<Asset> returnAsset(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(assetService.returnAsset(id, body));
    }

    @PutMapping("/relieve/{id}")
    public ResponseEntity<Asset> relieveEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.relieveEmployee(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(Map.of("message", "Asset deleted successfully"));
    }

    /**
     * Sends the "Asset Assignment" notification email for this asset.
     * Also used for "Resend" from the Email Logs page — sending is idempotent
     * from the caller's point of view; each call just adds a new log row.
     * The admin identity is taken from the JWT subject, never from the request body.
     */
    @PostMapping("/send-email/{id}")
    public ResponseEntity<SendAssetEmailResponse> sendAssignmentEmail(@PathVariable Long id,
                                                                       Authentication authentication) {
        String sentBy = authentication != null ? authentication.getName() : "unknown";
        Asset updated = assetEmailService.sendAssignmentEmail(id, sentBy);
        return ResponseEntity.ok(new SendAssetEmailResponse(updated, "Asset assignment email sent successfully."));
    }

    @GetMapping("/email-logs")
    public List<AssetEmailLogResponse> getEmailLogs() {
        return assetEmailService.getEmailLogs();
    }
}
