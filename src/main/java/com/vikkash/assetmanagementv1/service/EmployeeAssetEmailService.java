package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.EmployeeAssetEmailLogResponse;
import com.vikkash.assetmanagementv1.dto.EmployeeAssetsBundleResponse;
import com.vikkash.assetmanagementv1.dto.EmployeeSearchResponse;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.EmployeeAssetEmailLog;
import com.vikkash.assetmanagementv1.exception.EmailDeliveryException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeAssetEmailLogRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for the enterprise "Send Asset Email" admin page:
 * search an employee, review every asset currently assigned to them, pick
 * which ones to include, and email them all in a single professional
 * notification. Every attempt (success or failure) is written to
 * employee_asset_email_logs so the "Asset Email Logs" page has full history,
 * with a Resend action that always re-reads current data before sending again.
 */
@Service
public class EmployeeAssetEmailService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAssetEmailService.class);
    private static final int SEARCH_RESULT_LIMIT = 20;

    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final EmployeeAssetEmailLogRepository emailLogRepository;
    private final EmailService emailService;

    public EmployeeAssetEmailService(EmployeeRepository employeeRepository,
                                      AssetRepository assetRepository,
                                      EmployeeAssetEmailLogRepository emailLogRepository,
                                      EmailService emailService) {
        this.employeeRepository = employeeRepository;
        this.assetRepository = assetRepository;
        this.emailLogRepository = emailLogRepository;
        this.emailService = emailService;
    }

    /** Searches employees by Employee ID, Employee Name, or Email (partial, case-insensitive). */
    @Transactional(readOnly = true)
    public List<EmployeeSearchResponse> searchEmployees(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return employeeRepository
                .findByEmployeeIdContainingIgnoreCaseOrEmployeeNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, q)
                .stream()
                .sorted(Comparator.comparing(Employee::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(SEARCH_RESULT_LIMIT)
                .map(EmployeeSearchResponse::from)
                .toList();
    }

    /** Employee detail + every asset currently assigned to them, for the page's detail panel. */
    @Transactional(readOnly = true)
    public EmployeeAssetsBundleResponse getEmployeeWithAssets(String employeeId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        List<Asset> assignedAssets = assetRepository.findByEmployeeId(employee.getEmployeeId()).stream()
                .filter(a -> "Assigned".equals(a.getAssetStatus()))
                .sorted(Comparator.comparing(Asset::getAssetId))
                .toList();

        return new EmployeeAssetsBundleResponse(EmployeeSearchResponse.from(employee), assignedAssets);
    }

    /**
     * Sends the bulk asset email for the given employee + the admin-selected
     * asset IDs. Always re-reads employee and asset data from PostgreSQL
     * before sending, so the email reflects the latest data. Every attempt
     * (success or failure) is logged. Throws EmailDeliveryException on
     * failure (after logging it) so the frontend gets a clear error.
     */
    @Transactional
    public EmployeeAssetEmailLogResponse sendBulkAssetEmail(String employeeId, List<Long> assetIds, String sentByAdmin) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with ID: " + employeeId));

        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Employee '" + employee.getEmployeeName() + "' has no email address on file.");
        }

        List<Asset> assets = resolveAndValidateAssets(employee, assetIds);

        return sendAndLog(employee, assets, sentByAdmin);
    }

    /**
     * Resends a previous bulk email. Re-resolves the employee and the
     * originally-selected assets against current data (an asset that has
     * since been returned or reassigned elsewhere is dropped rather than
     * failing the whole resend), then creates a NEW log row — the original
     * row is left untouched so full history is preserved.
     */
    @Transactional
    public EmployeeAssetEmailLogResponse resend(Long logId, String sentByAdmin) {
        EmployeeAssetEmailLog original = emailLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Email log not found with id: " + logId));

        Employee employee = employeeRepository.findByEmployeeId(original.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with ID: " + original.getEmployeeId() + " — cannot resend."));

        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Employee '" + employee.getEmployeeName() + "' has no email address on file.");
        }

        List<Long> assetIds = parseAssetIds(original.getAssetIds());
        List<Asset> currentAssets = assetRepository.findAllById(assetIds).stream()
                .filter(a -> employee.getEmployeeId().equalsIgnoreCase(a.getEmployeeId())
                        && "Assigned".equals(a.getAssetStatus()))
                .sorted(Comparator.comparing(Asset::getAssetId))
                .toList();

        if (currentAssets.isEmpty()) {
            throw new IllegalArgumentException(
                    "None of the assets in this email are still assigned to " + employee.getEmployeeName()
                            + ". Please use \"Send Asset Email\" to send a fresh selection instead.");
        }

        return sendAndLog(employee, currentAssets, sentByAdmin);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssetEmailLogResponse> getEmailLogs() {
        return emailLogRepository.findAllByOrderBySentAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private List<Asset> resolveAndValidateAssets(Employee employee, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one asset to include in the email.");
        }

        List<Asset> assets = assetRepository.findAllById(assetIds);

        if (assets.size() != assetIds.size()) {
            throw new ResourceNotFoundException("One or more selected assets could not be found.");
        }

        List<Asset> mismatched = assets.stream()
                .filter(a -> !employee.getEmployeeId().equalsIgnoreCase(a.getEmployeeId())
                        || !"Assigned".equals(a.getAssetStatus()))
                .toList();

        if (!mismatched.isEmpty()) {
            throw new IllegalArgumentException(
                    "One or more selected assets are not currently assigned to " + employee.getEmployeeName()
                            + ". Please refresh and try again.");
        }

        return assets.stream().sorted(Comparator.comparing(Asset::getAssetId)).toList();
    }

    private EmployeeAssetEmailLogResponse sendAndLog(Employee employee, List<Asset> assets, String sentByAdmin) {
        EmailService.BulkEmailEmployeeDetails details = new EmailService.BulkEmailEmployeeDetails(
                employee.getEmployeeName(), employee.getEmployeeId(),
                employee.getDepartment(), employee.getDesignation(), employee.getLocation());

        List<EmailService.BulkAssetRow> rows = assets.stream()
                .map(a -> new EmailService.BulkAssetRow(
                        a.getAssetId(), a.getAssetType(), a.getLaptopName(), a.getBrand(),
                        a.getModel(), a.getSerialNumber(), a.getLocation(), a.getAssignedDate()))
                .toList();

        String assetIdsCsv = assets.stream().map(a -> String.valueOf(a.getAssetId())).collect(Collectors.joining(","));
        String assetsSummary = assets.stream()
                .map(a -> (a.getLaptopName() == null ? "Asset #" + a.getAssetId() : a.getLaptopName())
                        + " (" + (a.getSerialNumber() == null ? "—" : a.getSerialNumber()) + ")")
                .collect(Collectors.joining(", "));

        try {
            emailService.sendBulkAssetAssignmentEmail(employee.getEmail(), details, rows);

            EmployeeAssetEmailLog logEntry = saveLog(employee, assetIdsCsv, assetsSummary, assets.size(),
                    sentByAdmin, "SENT", null);
            log.info("Bulk asset email sent for employee {} ({} asset(s))", employee.getEmployeeId(), assets.size());
            return toResponse(logEntry);

        } catch (EmailDeliveryException ex) {
            // Persist the failure log (so it shows up in Asset Email Logs), but still surface the error to the caller.
            saveLog(employee, assetIdsCsv, assetsSummary, assets.size(), sentByAdmin, "FAILED", ex.getMessage());
            log.error("Bulk asset email failed for employee {}: {}", employee.getEmployeeId(), ex.getMessage());
            throw ex;
        }
    }

    private EmployeeAssetEmailLog saveLog(Employee employee, String assetIdsCsv, String assetsSummary, int count,
                                           String sentByAdmin, String status, String errorMessage) {
        EmployeeAssetEmailLog logEntry = new EmployeeAssetEmailLog();
        logEntry.setEmployeeId(employee.getEmployeeId());
        logEntry.setEmployeeName(employee.getEmployeeName());
        logEntry.setEmployeeEmail(employee.getEmail());
        logEntry.setAssetIds(assetIdsCsv);
        logEntry.setAssetsSummary(assetsSummary);
        logEntry.setAssetCount(count);
        logEntry.setSentByAdmin(sentByAdmin);
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        return emailLogRepository.save(logEntry);
    }

    private List<Long> parseAssetIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // skip malformed fragments rather than failing the whole resend
                }
            }
        }
        return ids;
    }

    private EmployeeAssetEmailLogResponse toResponse(EmployeeAssetEmailLog logEntry) {
        return new EmployeeAssetEmailLogResponse(
                logEntry.getId(),
                logEntry.getEmployeeId(),
                logEntry.getEmployeeName(),
                logEntry.getEmployeeEmail(),
                parseAssetIds(logEntry.getAssetIds()),
                logEntry.getAssetsSummary(),
                logEntry.getAssetCount(),
                logEntry.getSentByAdmin(),
                logEntry.getSentAt(),
                logEntry.getStatus(),
                logEntry.getErrorMessage()
        );
    }
}
