package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.MaintenanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Asset maintenance lifecycle: scheduling preventive/corrective work,
 * tracking status through to completion, and recording cost/vendor history.
 * When a record moves to "Assigned"/"Under Repair" style states the asset's
 * own status is kept in sync so the Inventory list reflects reality.
 */
@Service
public class MaintenanceService {

    private final MaintenanceRecordRepository repository;
    private final AssetRepository assetRepository;
    private final AuditLogService auditLogService;

    public MaintenanceService(MaintenanceRecordRepository repository,
                               AssetRepository assetRepository,
                               AuditLogService auditLogService) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getForAsset(Long assetId) {
        return repository.findByAssetIdOrderByCreatedAtDesc(assetId);
    }

    @Transactional(readOnly = true)
    public MaintenanceRecord getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        return Map.of(
                "scheduled", repository.countByStatus("Scheduled"),
                "inProgress", repository.countByStatus("In Progress"),
                "completed", repository.countByStatus("Completed"),
                "cancelled", repository.countByStatus("Cancelled")
        );
    }

    @Transactional
    public MaintenanceRecord create(MaintenanceRecord record, String createdBy) {
        Asset asset = assetRepository.findById(record.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + record.getAssetId()));

        record.setCreatedBy(createdBy);
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("Scheduled");
        }
        MaintenanceRecord saved = repository.save(record);

        // Puts the asset "Under Repair" while active corrective maintenance is in flight,
        // so it can't be accidentally assigned to someone while it's out for service.
        if (("Scheduled".equals(saved.getStatus()) || "In Progress".equals(saved.getStatus()))
                && "Corrective".equalsIgnoreCase(saved.getMaintenanceType())) {
            asset.setAssetStatus("Under Repair");
            assetRepository.save(asset);
        }

        auditLogService.record("ASSET", String.valueOf(asset.getAssetId()), "MAINTENANCE_CREATED",
                "Maintenance (" + saved.getMaintenanceType() + ") scheduled for '" + asset.getLaptopName()
                        + "': " + (saved.getDescription() != null ? saved.getDescription() : ""), createdBy);

        return saved;
    }

    @Transactional
    public MaintenanceRecord update(Long id, MaintenanceRecord updated, String updatedBy) {
        MaintenanceRecord existing = getById(id);

        existing.setMaintenanceType(updated.getMaintenanceType());
        existing.setDescription(updated.getDescription());
        existing.setStatus(updated.getStatus());
        existing.setScheduledDate(updated.getScheduledDate());
        existing.setCompletedDate(updated.getCompletedDate());
        existing.setVendor(updated.getVendor());
        existing.setCost(updated.getCost());
        existing.setPerformedBy(updated.getPerformedBy());
        existing.setNextMaintenanceDate(updated.getNextMaintenanceDate());
        existing.setRemarks(updated.getRemarks());

        MaintenanceRecord saved = repository.save(existing);

        // Once maintenance completes, put the asset back into circulation.
        Asset asset = assetRepository.findById(existing.getAssetId()).orElse(null);
        if (asset != null && "Completed".equalsIgnoreCase(saved.getStatus())
                && "Under Repair".equalsIgnoreCase(asset.getAssetStatus())) {
            asset.setAssetStatus("Available");
            assetRepository.save(asset);
        }

        auditLogService.record("ASSET", String.valueOf(existing.getAssetId()), "MAINTENANCE_UPDATED",
                "Maintenance record #" + id + " updated to status '" + saved.getStatus() + "'", updatedBy);

        return saved;
    }

    @Transactional
    public void delete(Long id, String deletedBy) {
        MaintenanceRecord existing = getById(id);
        repository.delete(existing);
        auditLogService.record("ASSET", String.valueOf(existing.getAssetId()), "MAINTENANCE_DELETED",
                "Maintenance record #" + id + " deleted", deletedBy);
    }
}
