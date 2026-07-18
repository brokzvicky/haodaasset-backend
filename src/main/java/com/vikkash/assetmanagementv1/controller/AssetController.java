package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AssetEmailLogResponse;
import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.dto.BulkAssetUpdateRequest;
import com.vikkash.assetmanagementv1.dto.OrphanedAssetDTO;
import com.vikkash.assetmanagementv1.dto.RepairResultDTO;
import com.vikkash.assetmanagementv1.dto.SendAssetEmailResponse;
import com.vikkash.assetmanagementv1.dto.TimelineEventDTO;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.service.AssetEmailService;
import com.vikkash.assetmanagementv1.service.AssetService;
import com.vikkash.assetmanagementv1.service.AssetTimelineService;
import com.vikkash.assetmanagementv1.service.QrCodeService;
import com.vikkash.assetmanagementv1.service.TemporaryAssignmentReminderService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
    private final AssetTimelineService assetTimelineService;
    private final QrCodeService qrCodeService;

    public AssetController(AssetService assetService, AssetEmailService assetEmailService,
                            TemporaryAssignmentReminderService temporaryAssignmentReminderService,
                            AssetTimelineService assetTimelineService,
                            QrCodeService qrCodeService) {
        this.assetService = assetService;
        this.assetEmailService = assetEmailService;
        this.temporaryAssignmentReminderService = temporaryAssignmentReminderService;
        this.assetTimelineService = assetTimelineService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    /**
     * Fetch a single asset by its ID — powers the dedicated Asset Details
     * page (/assets/:id) so it can load directly (e.g. on refresh or a
     * shared link) without depending on the full asset list already being
     * in memory. Throws ResourceNotFoundException (→ 404 via
     * GlobalExceptionHandler) when no asset exists with that ID.
     */
    @GetMapping("/{id}")
    public Asset getAssetById(@PathVariable Long id) {
        return assetService.getById(id);
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

    /**
     * Returns an asset. If the request body includes "sendReturnEmail": "true",
     * the "Asset Return Confirmation" email is sent to the employee as part of
     * this same call (see AssetService.returnAsset) — same "ask, then act"
     * pattern the frontend already uses for the assignment email, just
     * collapsed into a single request since the employee link is cleared by
     * the return itself. The admin identity is taken from the JWT subject,
     * never from the request body.
     */
    @PutMapping("/return/{id}")
    public ResponseEntity<Asset> returnAsset(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body,
                                              Authentication authentication) {
        boolean sendReturnEmail = body != null && "true".equalsIgnoreCase(body.get("sendReturnEmail"));
        String sentBy = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(assetService.returnAsset(id, body, sendReturnEmail, sentBy));
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

    // ── QR Code Generation ──────────────────────────────────────────────────

    /** Streams a scannable PNG QR code that deep-links into this asset's record. */
    @GetMapping(value = "/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable Long id) throws Exception {
        Asset asset = assetService.getById(id);
        byte[] png = qrCodeService.generateAssetQrCode(id, asset.getSerialNumber());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    // ── Asset Timeline (Audit + Email + Maintenance + Documents merged) ────

    @GetMapping("/{id}/timeline")
    public List<TimelineEventDTO> getTimeline(@PathVariable Long id) {
        return assetTimelineService.getTimeline(id);
    }

    // ── Advanced Search & Filters ────────────────────────────────────────────

    @GetMapping("/search")
    public List<Asset> search(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String assetType,
                               @RequestParam(required = false) String assetStatus,
                               @RequestParam(required = false) String assetCondition,
                               @RequestParam(required = false) String location,
                               @RequestParam(required = false) String brand,
                               @RequestParam(required = false) String employeeId,
                               @RequestParam(required = false) String purchaseDateFrom,
                               @RequestParam(required = false) String purchaseDateTo,
                               @RequestParam(required = false) String warrantyExpiryFrom,
                               @RequestParam(required = false) String warrantyExpiryTo) {
        return assetService.search(keyword, assetType, assetStatus, assetCondition, location, brand, employeeId,
                purchaseDateFrom, purchaseDateTo, warrantyExpiryFrom, warrantyExpiryTo);
    }

    // ── Bulk Operations ───────────────────────────────────────────────────────

    @PutMapping("/bulk-update")
    public ResponseEntity<Map<String, Object>> bulkUpdate(@RequestBody BulkAssetUpdateRequest request,
                                                            Authentication authentication) {
        String performedBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.ok(assetService.bulkUpdate(request, performedBy));
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<Long>> body,
                                                            Authentication authentication) {
        String performedBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.ok(assetService.bulkDelete(body.get("assetIds"), performedBy));
    }
}
