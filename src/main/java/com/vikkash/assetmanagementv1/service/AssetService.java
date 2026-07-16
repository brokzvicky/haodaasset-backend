package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.dto.OrphanedAssetDTO;
import com.vikkash.assetmanagementv1.dto.RepairResultDTO;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.exception.DuplicateResourceException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.AssetRequestRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All asset-related business logic lives here.
 * Controllers only handle HTTP concerns (parsing, status codes, responses).
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final AssetRepository        assetRepository;
    private final EmployeeRepository     employeeRepository;
    private final AssetRequestRepository assetRequestRepository;
    private final AuditLogService        auditLogService;
    private final EmailService           emailService;
    private final AssetEmailService      assetEmailService;

    // Fixed inbox that every "asset assigned" admin notification goes to,
    // regardless of which admin performed the assignment — same pattern as
    // app.admin.2fa-email (a shared IT Support inbox, not a personal address).
    @Value("${app.admin.assignment-notification-email:itsupport@haodapayments.com}")
    private String assignmentNotificationEmail;

    public AssetService(AssetRepository assetRepository,
                        EmployeeRepository employeeRepository,
                        AssetRequestRepository assetRequestRepository,
                        AuditLogService auditLogService,
                        EmailService emailService,
                        AssetEmailService assetEmailService) {
        this.assetRepository        = assetRepository;
        this.employeeRepository     = employeeRepository;
        this.assetRequestRepository = assetRequestRepository;
        this.auditLogService        = auditLogService;
        this.emailService           = emailService;
        this.assetEmailService      = assetEmailService;
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Asset> getAvailableAssets() {
        return assetRepository.findByAssetStatus("Available");
    }

    @Transactional(readOnly = true)
    public Asset getById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Asset getBySerialNumber(String serialNumber) {
        return assetRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with serial number: " + serialNumber));
    }

    @Transactional(readOnly = true)
    public List<Asset> getByEmployee(String employeeName) {
        return assetRepository.findByEmployeeName(employeeName);
    }

    // ── Dashboard ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getDashboardStats() {
        return Map.of(
                "totalAssets",     assetRepository.count(),
                "availableAssets", assetRepository.countByAssetStatus("Available"),
                "assignedAssets",  assetRepository.countByAssetStatus("Assigned"),
                "spareAssets",     assetRepository.countByAssetStatus("Spare"),
                "underRepair",     assetRepository.countByAssetStatus("Under Repair"),
                "faultyAssets",    assetRepository.countByAssetStatus("Faulty"),
                "totalEmployees",  employeeRepository.count(),
                "pendingRequests", assetRequestRepository.countByStatus("PENDING")
        );
    }

    // ── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public Asset createAsset(Asset asset) {
        // Guard: serial number must be unique
        if (asset.getSerialNumber() != null && !asset.getSerialNumber().isBlank()) {
            if (assetRepository.existsBySerialNumber(asset.getSerialNumber())) {
                throw new DuplicateResourceException(
                        "An asset with serial number '" + asset.getSerialNumber() + "' already exists.");
            }
        }

        // New assets should never arrive pre-assigned
        asset.setEmployeeId(null);
        asset.setEmployeeName(null);
        asset.setEmployeeRole(null);
        asset.setAssignedDate(null);

        // Default status/condition if not provided
        if (asset.getAssetStatus() == null || asset.getAssetStatus().isBlank()) {
            asset.setAssetStatus("Available");
        }
        if (asset.getAssetCondition() == null || asset.getAssetCondition().isBlank()) {
            asset.setAssetCondition("New");
        }

        log.info("Creating new asset: type={} name={} serial={}",
                asset.getAssetType(), asset.getLaptopName(), asset.getSerialNumber());

        Asset saved = assetRepository.save(asset);
        auditLogService.record("ASSET", String.valueOf(saved.getAssetId()), "CREATED",
                "Added asset '" + saved.getLaptopName() + "' (SN: " + saved.getSerialNumber() + ")");
        return saved;
    }

    // ── Assign ─────────────────────────────────────────────────────────────

    @Transactional
    public Asset assignAsset(Long id, AssignAssetRequest request) {
        return assignAsset(id, request, null);
    }

    /**
     * @param assignedByAdmin username of the admin performing the assignment (may be null,
     *                         e.g. for internal/system calls). Recorded on the asset so that,
     *                         for a Temporary assignment, the "period expired" reminder email
     *                         can be routed back to the admin who made the assignment.
     */
    @Transactional
    public Asset assignAsset(Long id, AssignAssetRequest request, String assignedByAdmin) {
        Asset asset = getById(id);

        // Guard: cannot assign an asset that is not Available
        if (!"Available".equals(asset.getAssetStatus())) {
            throw new IllegalArgumentException(
                    "Asset '" + asset.getLaptopName() + "' is currently '" + asset.getAssetStatus()
                            + "' and cannot be assigned. Only Available assets can be assigned.");
        }

        // Guard: verify the employee exists in the employee table if an ID is given
        if (request.getEmployeeId() != null && !request.getEmployeeId().isBlank()) {
            if (!employeeRepository.existsByEmployeeId(request.getEmployeeId().trim().toUpperCase())) {
                throw new ResourceNotFoundException(
                        "Employee not found with ID: " + request.getEmployeeId());
            }
        }

        // Guard: assignment type must be Permanent or Temporary, and Temporary
        // assignments must include a reason and a duration.
        String assignmentType = (request.getAssignmentType() == null || request.getAssignmentType().isBlank())
                ? "Permanent" : request.getAssignmentType().trim();
        if (!"Permanent".equalsIgnoreCase(assignmentType) && !"Temporary".equalsIgnoreCase(assignmentType)) {
            throw new IllegalArgumentException("assignmentType must be either 'Permanent' or 'Temporary'.");
        }
        if ("Temporary".equalsIgnoreCase(assignmentType)) {
            if (request.getTemporaryReason() == null || request.getTemporaryReason().isBlank()) {
                throw new IllegalArgumentException("A reason is required for a temporary assignment.");
            }
            if (request.getTemporaryDurationDays() == null || request.getTemporaryDurationDays() <= 0) {
                throw new IllegalArgumentException("A valid duration (in days) is required for a temporary assignment.");
            }
        }

        asset.setEmployeeId(
                request.getEmployeeId() != null && !request.getEmployeeId().isBlank()
                        ? request.getEmployeeId().trim().toUpperCase()
                        : null
        );
        asset.setEmployeeName(request.getEmployeeName());
        asset.setEmployeeRole(request.getEmployeeRole());
        asset.setLocation(request.getLocation() != null ? request.getLocation() : asset.getLocation());
        String effectiveAssignedDate = request.getAssignedDate() != null
                ? request.getAssignedDate()
                : LocalDate.now().toString();
        asset.setAssignedDate(effectiveAssignedDate);
        asset.setReason(request.getRemarks());
        asset.setAssetStatus("Assigned");
        // Clear any previous return tracking on reassignment
        asset.setReturnedStatus(null);
        asset.setReturnDate(null);
        // New assignment ⇒ no assignment email has gone out for it yet
        asset.setEmailStatus("Not Sent");

        // Any issues flagged on the employee's previous/old asset (free text, optional)
        asset.setOldAssetIssues(request.getOldAssetIssues());
        asset.setAssignedByAdmin(assignedByAdmin);

        if ("Temporary".equalsIgnoreCase(assignmentType)) {
            LocalDate start;
            try {
                start = LocalDate.parse(effectiveAssignedDate);
            } catch (Exception ex) {
                start = LocalDate.now();
            }
            asset.setAssignmentType("Temporary");
            asset.setTemporaryReason(request.getTemporaryReason().trim());
            asset.setTemporaryDurationDays(request.getTemporaryDurationDays());
            asset.setTemporaryExpiryDate(start.plusDays(request.getTemporaryDurationDays()).toString());
            asset.setTemporaryReturnReminderSent("No");
        } else {
            asset.setAssignmentType("Permanent");
            asset.setTemporaryReason(null);
            asset.setTemporaryDurationDays(null);
            asset.setTemporaryExpiryDate(null);
            asset.setTemporaryReturnReminderSent("No");
        }

        log.info("Asset {} assigned to employee {} ({})", id, request.getEmployeeName(), asset.getAssignmentType());
        Asset saved = assetRepository.save(asset);
        String auditNote = "Assigned '" + saved.getLaptopName() + "' to " + saved.getEmployeeName()
                + (saved.getEmployeeId() != null ? " (" + saved.getEmployeeId() + ")" : "")
                + " — " + saved.getAssignmentType()
                + ("Temporary".equalsIgnoreCase(saved.getAssignmentType())
                        ? " (" + saved.getTemporaryDurationDays() + " day(s), reason: " + saved.getTemporaryReason()
                                + ", expires " + saved.getTemporaryExpiryDate() + ")"
                        : "");
        auditLogService.record("ASSET", String.valueOf(saved.getAssetId()), "ASSIGNED", auditNote);

        notifyAdminOfAssignment(saved, assignedByAdmin);

        return saved;
    }

    /**
     * Fires the automatic admin notification email right after a successful assignment,
     * with the complete assignment details (employee, asset, assignment type, date, etc.).
     * This is best-effort: any failure is logged but never rolls back or fails the
     * assignment itself, since the asset has already been assigned by this point.
     */
    private void notifyAdminOfAssignment(Asset saved, String assignedByAdmin) {
        String recipient = assignmentNotificationEmail;
        if (recipient == null || recipient.isBlank()) {
            log.warn("No admin notification email configured for assignment of asset {}; " +
                    "set app.admin.assignment-notification-email.", saved.getAssetId());
            return;
        }

        try {
            EmailService.AssetAssignmentAdminNotificationDetails details =
                    new EmailService.AssetAssignmentAdminNotificationDetails(
                            saved.getAssetId(),
                            saved.getLaptopName(),
                            saved.getAssetType(),
                            saved.getBrand(),
                            saved.getModel(),
                            saved.getSerialNumber(),
                            saved.getAssetCondition(),
                            saved.getLocation(),
                            saved.getEmployeeName(),
                            saved.getEmployeeId(),
                            saved.getEmployeeRole(),
                            saved.getAssignmentType(),
                            saved.getAssignedDate(),
                            assignedByAdmin,
                            saved.getReason(),
                            saved.getOldAssetIssues(),
                            saved.getTemporaryReason(),
                            saved.getTemporaryDurationDays(),
                            saved.getTemporaryExpiryDate()
                    );
            emailService.sendAssetAssignmentAdminNotification(recipient, details);
            log.info("Asset assignment admin notification sent for asset {} to {}", saved.getAssetId(), recipient);
        } catch (Exception ex) {
            log.error("Failed to send asset assignment admin notification for asset {}: {}",
                    saved.getAssetId(), ex.getMessage());
        }
    }

    /**
     * Diagnostic (read-only) scan: finds every asset whose assetStatus is
     * "Assigned" but whose employeeId link is broken — either missing, or
     * pointing at an employeeId that doesn't exist in the employee table.
     *
     * These are exactly the assets that will show a name in the Asset
     * Inventory list (via the free-text employeeName field) but will NOT
     * show up under that employee's "View Assets" panel, because that panel
     * looks the asset up strictly by employeeId
     * (see EmployeeService.getAssetsForEmployee).
     *
     * This method makes no changes — it only reports. Fixing an entry means
     * re-assigning that asset to the correct employee through the normal
     * "Assign Asset" flow so a valid employeeId gets written.
     */
    @Transactional(readOnly = true)
    public List<OrphanedAssetDTO> findOrphanedAssignments() {
        List<Asset> assignedAssets = assetRepository.findByAssetStatus("Assigned");
        List<OrphanedAssetDTO> orphaned = new ArrayList<>();

        for (Asset asset : assignedAssets) {
            String empId = asset.getEmployeeId();

            if (empId == null || empId.isBlank()) {
                orphaned.add(new OrphanedAssetDTO(
                        asset.getAssetId(),
                        asset.getLaptopName(),
                        asset.getSerialNumber(),
                        asset.getEmployeeName(),
                        empId,
                        "EMPLOYEE_ID_MISSING"
                ));
                continue;
            }

            boolean employeeExists = employeeRepository.existsByEmployeeId(empId.trim().toUpperCase());
            if (!employeeExists) {
                orphaned.add(new OrphanedAssetDTO(
                        asset.getAssetId(),
                        asset.getLaptopName(),
                        asset.getSerialNumber(),
                        asset.getEmployeeName(),
                        empId,
                        "EMPLOYEE_ID_NOT_FOUND"
                ));
            }
        }

        log.info("findOrphanedAssignments: {} of {} assigned asset(s) have a broken employeeId link.",
                orphaned.size(), assignedAssets.size());
        return orphaned;
    }

    /**
     * Repairs every orphaned assignment found by findOrphanedAssignments():
     * for each affected asset, clears the (broken) assignment fields and
     * resets assetStatus to "Available" — mirroring exactly what a normal
     * returnAsset() does to those fields (see returnAsset() below), minus
     * the returnedStatus/returnDate bookkeeping, since this was never a
     * real return.
     *
     * This does NOT try to guess which employee an asset "should" belong
     * to — it only undoes the broken link so the asset is free to be
     * correctly re-assigned via the normal Assign Asset flow. Nothing is
     * deleted; laptopName/serialNumber/condition/etc. are untouched.
     */
    @Transactional
    public List<RepairResultDTO> repairOrphanedAssignments() {
        List<Asset> assignedAssets = assetRepository.findByAssetStatus("Assigned");
        List<RepairResultDTO> repaired = new ArrayList<>();

        for (Asset asset : assignedAssets) {
            String empId = asset.getEmployeeId();
            String reason = null;

            if (empId == null || empId.isBlank()) {
                reason = "EMPLOYEE_ID_MISSING";
            } else if (!employeeRepository.existsByEmployeeId(empId.trim().toUpperCase())) {
                reason = "EMPLOYEE_ID_NOT_FOUND";
            }

            if (reason == null) {
                continue; // this asset's assignment is valid, leave it alone
            }

            String previousEmployeeName = asset.getEmployeeName();
            String previousEmployeeId = empId;

            asset.setEmployeeId(null);
            asset.setEmployeeName(null);
            asset.setEmployeeRole(null);
            asset.setAssignedDate(null);
            asset.setAssetStatus("Available");
            asset.setAssignmentType("Permanent");
            asset.setTemporaryReason(null);
            asset.setTemporaryDurationDays(null);
            asset.setTemporaryExpiryDate(null);
            asset.setTemporaryReturnReminderSent("No");
            asset.setAssignedByAdmin(null);
            asset.setOldAssetIssues(null);
            assetRepository.save(asset);

            repaired.add(new RepairResultDTO(
                    asset.getAssetId(),
                    asset.getLaptopName(),
                    asset.getSerialNumber(),
                    previousEmployeeName,
                    previousEmployeeId,
                    reason,
                    "Available"
            ));

            log.info("Repaired orphaned assignment on asset {} (was '{}', employeeId='{}', reason={}). Status reset to Available.",
                    asset.getAssetId(), previousEmployeeName, previousEmployeeId, reason);
        }

        log.info("repairOrphanedAssignments: repaired {} of {} assigned asset(s).",
                repaired.size(), assignedAssets.size());
        if (!repaired.isEmpty()) {
            auditLogService.record("ASSET", "-", "REPAIRED",
                    "Repaired " + repaired.size() + " orphaned asset assignment(s), reset to Available");
        }
        return repaired;
    }

    // ── Update ─────────────────────────────────────────────────────────────


    /**
     * Updates only the non‑null fields of the asset.
     * Also validates that the new serial number (if changed) is unique.
     * The asset status may be automatically derived from the condition.
     */
    @Transactional
    public Asset updateAsset(Long id, Asset updatedAsset) {
        Asset asset = getById(id);

        // If serial number is being changed, ensure it's unique
        if (updatedAsset.getSerialNumber() != null && !updatedAsset.getSerialNumber().isBlank()) {
            String newSerial = updatedAsset.getSerialNumber().trim();
            if (!newSerial.equals(asset.getSerialNumber()) &&
                    assetRepository.existsBySerialNumber(newSerial)) {
                throw new DuplicateResourceException(
                        "An asset with serial number '" + newSerial + "' already exists.");
            }
            asset.setSerialNumber(newSerial);
        }

        if (updatedAsset.getAssetType() != null) {
            asset.setAssetType(updatedAsset.getAssetType());
        }
        if (updatedAsset.getLaptopName() != null && !updatedAsset.getLaptopName().isBlank()) {
            asset.setLaptopName(updatedAsset.getLaptopName());
        }
        if (updatedAsset.getBrand() != null && !updatedAsset.getBrand().isBlank()) {
            asset.setBrand(updatedAsset.getBrand());
        }
        if (updatedAsset.getModel() != null) {
            asset.setModel(updatedAsset.getModel());
        }
        if (updatedAsset.getLocation() != null) {
            asset.setLocation(updatedAsset.getLocation());
        }
        if (updatedAsset.getVendor() != null) {
            asset.setVendor(updatedAsset.getVendor());
        }
        if (updatedAsset.getAssetCost() != null) {
            asset.setAssetCost(updatedAsset.getAssetCost());
        }
        if (updatedAsset.getPurchaseDate() != null) {
            asset.setPurchaseDate(updatedAsset.getPurchaseDate());
        }
        if (updatedAsset.getWarrantyExpiry() != null) {
            asset.setWarrantyExpiry(updatedAsset.getWarrantyExpiry());
        }
        if (updatedAsset.getRemarks() != null) {
            asset.setRemarks(updatedAsset.getRemarks());
        }

        // Update condition and possibly status based on it
        if (updatedAsset.getAssetCondition() != null) {
            asset.setAssetCondition(updatedAsset.getAssetCondition());
            switch (updatedAsset.getAssetCondition()) {
                case "Faulty":
                    asset.setAssetStatus("Faulty");
                    break;
                case "Damaged":
                    asset.setAssetStatus("Under Repair");
                    break;
                case "New":
                case "Excellent":
                case "Good":
                case "Fair":
                    // Only make Available if not currently assigned
                    if (!"Assigned".equals(asset.getAssetStatus())) {
                        asset.setAssetStatus("Available");
                    }
                    break;
                // default: leave status unchanged
            }
        }

        log.info("Asset {} updated", id);
        Asset saved = assetRepository.save(asset);
        auditLogService.record("ASSET", String.valueOf(saved.getAssetId()), "UPDATED",
                "Updated details for '" + saved.getLaptopName() + "'");
        return saved;
    }

    // ── Return ─────────────────────────────────────────────────────────────

    @Transactional
    public Asset returnAsset(Long id, Map<String, String> body) {
        return returnAsset(id, body, false, null);
    }

    /**
     * @param sendReturnEmail whether to send the "Asset Return Confirmation" email
     *                        to the employee as part of this return. When true, the
     *                        email is sent BEFORE the asset's employee link is cleared
     *                        (below), since that link — and the employee's name/email —
     *                        no longer resolve once the return completes. If the send
     *                        fails, the exception propagates and this whole transaction
     *                        rolls back, so the return is not silently completed without
     *                        the employee being notified.
     * @param sentByAdmin     admin username performing the return (may be null), recorded
     *                        on the email log the same way sendAssignmentEmail does.
     */
    @Transactional
    public Asset returnAsset(Long id, Map<String, String> body, boolean sendReturnEmail, String sentByAdmin) {
        Asset asset = getById(id);

        if (!"Assigned".equals(asset.getAssetStatus())) {
            throw new IllegalArgumentException(
                    "Asset '" + asset.getLaptopName() + "' is not currently Assigned and cannot be returned.");
        }

        String nextStatus = (body != null && body.get("assetStatus") != null)
                ? body.get("assetStatus") : "Available";
        String returnedCondition = (body != null && body.get("condition") != null)
                ? body.get("condition") : null;
        String returnDate = LocalDate.now().toString();

        if (sendReturnEmail) {
            String employeeId = asset.getEmployeeId();
            if (employeeId == null || employeeId.isBlank()) {
                throw new IllegalArgumentException(
                        "This asset has no linked employee record, so no return email can be sent.");
            }
            Employee employee = employeeRepository.findByEmployeeId(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Employee not found with ID: " + employeeId));
            assetEmailService.sendReturnEmail(asset, employee, sentByAdmin, returnDate);
        }

        asset.setReturnedStatus("Yes");
        asset.setReturnDate(returnDate);
        asset.setAssetStatus(nextStatus);
        if (returnedCondition != null) {
            asset.setAssetCondition(returnedCondition);
        }

        String previousEmployeeName = asset.getEmployeeName();

        // Clear assignment fields
        asset.setEmployeeId(null);
        asset.setEmployeeName(null);
        asset.setEmployeeRole(null);
        asset.setAssignedDate(null);
        asset.setEmailStatus("Not Sent");
        // Clear temporary-assignment tracking so it never carries over to the next assignment
        asset.setAssignmentType("Permanent");
        asset.setTemporaryReason(null);
        asset.setTemporaryDurationDays(null);
        asset.setTemporaryExpiryDate(null);
        asset.setTemporaryReturnReminderSent("No");
        asset.setAssignedByAdmin(null);
        asset.setOldAssetIssues(null);

        log.info("Asset {} returned. New status: {}", id, nextStatus);
        Asset saved = assetRepository.save(asset);
        auditLogService.record("ASSET", String.valueOf(saved.getAssetId()), "RETURNED",
                "'" + saved.getLaptopName() + "' returned by " + (previousEmployeeName != null ? previousEmployeeName : "unknown")
                        + " → status set to " + nextStatus
                        + (sendReturnEmail ? " (return email sent)" : ""));
        return saved;
    }

    // ── Relieve ────────────────────────────────────────────────────────────

    @Transactional
    public Asset relieveEmployee(Long id) {
        Asset asset = getById(id);
        asset.setRelievedStatus("Yes");
        asset.setRelievedDate(LocalDate.now().toString());
        Asset saved = assetRepository.save(asset);
        auditLogService.record("ASSET", String.valueOf(saved.getAssetId()), "RELIEVED",
                "Marked '" + saved.getLaptopName() + "' holder (" + saved.getEmployeeName() + ") as relieved");
        return saved;
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + id));
        log.warn("Deleting asset id={}", id);
        assetRepository.deleteById(id);
        auditLogService.record("ASSET", String.valueOf(id), "DELETED",
                "Deleted asset '" + asset.getLaptopName() + "' (SN: " + asset.getSerialNumber() + ")");
    }
}