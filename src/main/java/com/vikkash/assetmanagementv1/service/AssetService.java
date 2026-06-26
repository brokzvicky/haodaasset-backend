package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.AssignAssetRequest;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.exception.DuplicateResourceException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.AssetRequestRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public AssetService(AssetRepository assetRepository,
                        EmployeeRepository employeeRepository,
                        AssetRequestRepository assetRequestRepository) {
        this.assetRepository        = assetRepository;
        this.employeeRepository     = employeeRepository;
        this.assetRequestRepository = assetRequestRepository;
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

        return assetRepository.save(asset);
    }

    // ── Assign ─────────────────────────────────────────────────────────────

    @Transactional
    public Asset assignAsset(Long id, AssignAssetRequest request) {
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

        asset.setEmployeeId(request.getEmployeeId());
        asset.setEmployeeName(request.getEmployeeName());
        asset.setEmployeeRole(request.getEmployeeRole());
        asset.setLocation(request.getLocation() != null ? request.getLocation() : asset.getLocation());
        asset.setAssignedDate(
                request.getAssignedDate() != null
                        ? request.getAssignedDate()
                        : LocalDate.now().toString()
        );
        asset.setReason(request.getRemarks());
        asset.setAssetStatus("Assigned");
        // Clear any previous return tracking on reassignment
        asset.setReturnedStatus(null);
        asset.setReturnDate(null);

        log.info("Asset {} assigned to employee {}", id, request.getEmployeeName());
        return assetRepository.save(asset);
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
        return assetRepository.save(asset);
    }

    // ── Return ─────────────────────────────────────────────────────────────

    @Transactional
    public Asset returnAsset(Long id, Map<String, String> body) {
        Asset asset = getById(id);

        if (!"Assigned".equals(asset.getAssetStatus())) {
            throw new IllegalArgumentException(
                    "Asset '" + asset.getLaptopName() + "' is not currently Assigned and cannot be returned.");
        }

        String nextStatus = (body != null && body.get("assetStatus") != null)
                ? body.get("assetStatus") : "Available";
        String returnedCondition = (body != null && body.get("condition") != null)
                ? body.get("condition") : null;

        asset.setReturnedStatus("Yes");
        asset.setReturnDate(LocalDate.now().toString());
        asset.setAssetStatus(nextStatus);
        if (returnedCondition != null) {
            asset.setAssetCondition(returnedCondition);
        }

        // Clear assignment fields
        asset.setEmployeeId(null);
        asset.setEmployeeName(null);
        asset.setEmployeeRole(null);
        asset.setAssignedDate(null);

        log.info("Asset {} returned. New status: {}", id, nextStatus);
        return assetRepository.save(asset);
    }

    // ── Relieve ────────────────────────────────────────────────────────────

    @Transactional
    public Asset relieveEmployee(Long id) {
        Asset asset = getById(id);
        asset.setRelievedStatus("Yes");
        asset.setRelievedDate(LocalDate.now().toString());
        return assetRepository.save(asset);
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteAsset(Long id) {
        if (!assetRepository.existsById(id)) {
            throw new ResourceNotFoundException("Asset not found with id: " + id);
        }
        log.warn("Deleting asset id={}", id);
        assetRepository.deleteById(id);
    }
}