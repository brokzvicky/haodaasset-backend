package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a physical or virtual IT asset tracked in inventory.
 *
 * Design note: employee fields (employeeId, employeeName, employeeRole) are
 * denormalized onto the asset row intentionally — they record who held the
 * asset at assignment time and remain visible in audit history even if the
 * employee record is later updated or deleted.
 */
@Entity
@Table(
    name = "assets",
    indexes = {
        @Index(name = "idx_asset_status",      columnList = "assetStatus"),
        @Index(name = "idx_asset_employee_id", columnList = "employeeId"),
        @Index(name = "idx_asset_serial",      columnList = "serialNumber", unique = true)
    }
)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long assetId;

    // ── Employee assignment fields (null when unassigned) ──────────────────
    private String employeeId;
    private String employeeName;
    private String employeeRole;

    // ── Core asset fields ──────────────────────────────────────────────────
    @NotBlank(message = "Asset type is required")
    private String assetType;

    @NotBlank(message = "Asset name is required")
    private String laptopName;        // historical name kept for DB/frontend compat

    @NotBlank(message = "Brand is required")
    private String brand;

    private String model;

    @Column(unique = true)
    private String serialNumber;

    private String location;

    @Column(name = "asset_status")
    private String assetStatus = "Available";

    /**
     * Tracks whether the "Asset Assignment" notification email has been sent
     * for the current assignment. One of: "Not Sent", "Sent", "Failed".
     * Reset to "Not Sent" whenever the asset is (re)assigned or returned so a
     * stale status from a previous assignment never carries over.
     */
    @Column(name = "email_status")
    private String emailStatus = "Not Sent";


    @Column(name = "asset_condition")
    private String assetCondition = "New";

    // ── Optional procurement fields ────────────────────────────────────────
    private String vendor;

    @Column(name = "asset_cost")
    private String assetCost;

    @Column(name = "purchase_date")
    private String purchaseDate;

    @Column(name = "warranty_expiry")
    private String warrantyExpiry;

    private String remarks;

    // ── Hardware specification fields ──────────────────────────────────────
    private String processor;
    private String ram;
    private String storage;

    // ── Assignment / return tracking ───────────────────────────────────────
    private String assignedDate;
    private String returnedStatus;
    private String returnDate;
    private String reason;
    private String relievedStatus;
    private String relievedDate;

    public Asset() {}

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeRole() { return employeeRole; }
    public void setEmployeeRole(String employeeRole) { this.employeeRole = employeeRole; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getLaptopName() { return laptopName; }
    public void setLaptopName(String laptopName) { this.laptopName = laptopName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAssetStatus() { return assetStatus; }
    public void setAssetStatus(String assetStatus) { this.assetStatus = assetStatus; }

    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }

    public String getAssetCondition() { return assetCondition; }
    public void setAssetCondition(String assetCondition) { this.assetCondition = assetCondition; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getAssetCost() { return assetCost; }
    public void setAssetCost(String assetCost) { this.assetCost = assetCost; }

    public String getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getWarrantyExpiry() { return warrantyExpiry; }
    public void setWarrantyExpiry(String warrantyExpiry) { this.warrantyExpiry = warrantyExpiry; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public String getRam() { return ram; }
    public void setRam(String ram) { this.ram = ram; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getAssignedDate() { return assignedDate; }
    public void setAssignedDate(String assignedDate) { this.assignedDate = assignedDate; }

    public String getReturnedStatus() { return returnedStatus; }
    public void setReturnedStatus(String returnedStatus) { this.returnedStatus = returnedStatus; }

    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRelievedStatus() { return relievedStatus; }
    public void setRelievedStatus(String relievedStatus) { this.relievedStatus = relievedStatus; }

    public String getRelievedDate() { return relievedDate; }
    public void setRelievedDate(String relievedDate) { this.relievedDate = relievedDate; }
}
