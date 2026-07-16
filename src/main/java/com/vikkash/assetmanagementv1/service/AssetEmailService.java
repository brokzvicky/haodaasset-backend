package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AssetEmailLogResponse;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.AssetEmailLog;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.exception.EmailDeliveryException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetEmailLogRepository;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the "Send Asset Assignment Email" feature.
 * Always re-reads the asset and employee from PostgreSQL before sending, so
 * the email reflects the latest data even if it was edited after assignment.
 * Every attempt (success or failure) is written to asset_email_logs, and the
 * asset's emailStatus column is kept in sync for the Assets table pill.
 */
@Service
public class AssetEmailService {

    private static final Logger log = LoggerFactory.getLogger(AssetEmailService.class);

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final AssetEmailLogRepository emailLogRepository;
    private final EmailService emailService;

    public AssetEmailService(AssetRepository assetRepository,
                              EmployeeRepository employeeRepository,
                              AssetEmailLogRepository emailLogRepository,
                              EmailService emailService) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.emailLogRepository = emailLogRepository;
        this.emailService = emailService;
    }

    /**
     * Sends (or resends) the assignment email for the given asset.
     * Throws EmailDeliveryException on failure (after logging it), so the
     * frontend gets a clear error via GlobalExceptionHandler — the caller
     * does not need to inspect a status field.
     */
    @Transactional
    public Asset sendAssignmentEmail(Long assetId, String sentByAdmin) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        if (!"Assigned".equals(asset.getAssetStatus())) {
            throw new IllegalArgumentException(
                    "This asset is not currently assigned to anyone, so no assignment email can be sent.");
        }

        if (asset.getEmployeeId() == null || asset.getEmployeeId().isBlank()) {
            throw new IllegalArgumentException(
                    "This asset has no linked employee record, so no assignment email can be sent. " +
                            "Please re-assign it through the Assign Asset flow first.");
        }

        Employee employee = employeeRepository.findByEmployeeId(asset.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with ID: " + asset.getEmployeeId()));

        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Employee '" + employee.getEmployeeName() + "' has no email address on file.");
        }

        EmailService.AssetAssignmentEmailDetails details = new EmailService.AssetAssignmentEmailDetails(
                asset.getAssetId(),
                asset.getLaptopName(),
                asset.getBrand(),
                asset.getModel(),
                asset.getSerialNumber(),
                asset.getAssignedDate(),
                asset.getLocation()
        );

        try {
            emailService.sendAssetAssignmentEmail(
                    employee.getEmail(), employee.getEmployeeName(), employee.getEmployeeId(), details);

            saveLog(asset, employee, sentByAdmin, "SENT", null, "ASSIGNMENT");
            asset.setEmailStatus("Sent");
            log.info("Assignment email sent for asset {} to employee {}", assetId, employee.getEmployeeId());
            return assetRepository.save(asset);

        } catch (EmailDeliveryException ex) {
            saveLog(asset, employee, sentByAdmin, "FAILED", ex.getMessage(), "ASSIGNMENT");
            asset.setEmailStatus("Failed");
            assetRepository.save(asset);
            log.error("Assignment email failed for asset {} to employee {}: {}",
                    assetId, employee.getEmployeeId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Sends the "Asset Return Confirmation" email for an asset that is about to
     * be returned. Unlike sendAssignmentEmail (which re-reads the asset by id),
     * this takes the already-loaded Asset and Employee directly: the caller
     * (AssetService.returnAsset) must invoke this BEFORE it clears the asset's
     * employee link, since that link no longer exists once the return completes.
     * Reuses the same EmailService call and asset_email_logs logging as the
     * assignment email — only the template and log's emailType differ.
     *
     * @param asset        the asset being returned (still linked to its employee)
     * @param employee     the employee returning the asset
     * @param sentByAdmin  admin username who triggered the return (may be null)
     * @param returnDate   the return date to render into the email (yyyy-MM-dd)
     * @throws EmailDeliveryException on failure (after logging it), so the caller's
     *                                transaction can roll back the return itself.
     */
    @Transactional
    public void sendReturnEmail(Asset asset, Employee employee, String sentByAdmin, String returnDate) {
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Employee '" + employee.getEmployeeName() + "' has no email address on file.");
        }

        EmailService.AssetReturnEmailDetails details = new EmailService.AssetReturnEmailDetails(
                asset.getAssetId(),
                asset.getLaptopName(),
                asset.getAssetType(),
                String.valueOf(asset.getAssetId()),
                asset.getSerialNumber(),
                asset.getBrand(),
                asset.getModel(),
                returnDate
        );

        try {
            emailService.sendAssetReturnEmail(
                    employee.getEmail(), employee.getEmployeeName(), employee.getEmployeeId(), details);

            saveLog(asset, employee, sentByAdmin, "SENT", null, "RETURN");
            log.info("Return email sent for asset {} to employee {}", asset.getAssetId(), employee.getEmployeeId());
        } catch (EmailDeliveryException ex) {
            saveLog(asset, employee, sentByAdmin, "FAILED", ex.getMessage(), "RETURN");
            log.error("Return email failed for asset {} to employee {}: {}",
                    asset.getAssetId(), employee.getEmployeeId(), ex.getMessage());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AssetEmailLogResponse> getEmailLogs() {
        return emailLogRepository.findAllByOrderBySentAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private void saveLog(Asset asset, Employee employee, String sentByAdmin, String status,
                          String errorMessage, String emailType) {
        AssetEmailLog logEntry = new AssetEmailLog();
        logEntry.setAssetId(asset.getAssetId());
        logEntry.setEmployeeId(employee.getEmployeeId());
        logEntry.setEmployeeEmail(employee.getEmail());
        logEntry.setSentByAdmin(sentByAdmin);
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setEmailType(emailType);
        emailLogRepository.save(logEntry);
    }

    private AssetEmailLogResponse toResponse(AssetEmailLog logEntry) {
        // Best-effort enrichment: asset/employee may have since changed or been
        // deleted, so fall back to a safe label instead of failing the whole list.
        String assetLabel = assetRepository.findById(logEntry.getAssetId())
                .map(a -> a.getLaptopName() + " (" + a.getSerialNumber() + ")")
                .orElse("Asset #" + logEntry.getAssetId());

        String employeeName = employeeRepository.findByEmployeeId(logEntry.getEmployeeId())
                .map(Employee::getEmployeeName)
                .orElse(logEntry.getEmployeeId());

        return new AssetEmailLogResponse(
                logEntry.getId(),
                logEntry.getAssetId(),
                assetLabel,
                logEntry.getEmployeeId(),
                employeeName,
                logEntry.getEmployeeEmail(),
                logEntry.getSentByAdmin(),
                logEntry.getSentAt(),
                logEntry.getStatus(),
                logEntry.getErrorMessage(),
                logEntry.getEmailType()
        );
    }
}
