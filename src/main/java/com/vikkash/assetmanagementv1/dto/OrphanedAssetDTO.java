package com.vikkash.assetmanagementv1.dto;

/**
 * Represents one "Assigned" asset whose employeeId link is broken —
 * i.e. it shows a name in the Asset Inventory list but will NOT show up
 * under that employee's "View Assets" panel, because that panel looks up
 * assets strictly by employeeId, not by the free-text employeeName.
 *
 * reason is one of:
 *   "EMPLOYEE_ID_MISSING"   - employeeId is null/blank on the asset
 *   "EMPLOYEE_ID_NOT_FOUND" - employeeId is set but no employee row matches it
 */
public class OrphanedAssetDTO {

    private Long assetId;
    private String laptopName;
    private String serialNumber;
    private String employeeName;   // what the Asset Inventory page displays
    private String employeeId;     // what's actually stored (may be null)
    private String reason;

    public OrphanedAssetDTO() {
    }

    public OrphanedAssetDTO(Long assetId, String laptopName, String serialNumber,
                             String employeeName, String employeeId, String reason) {
        this.assetId = assetId;
        this.laptopName = laptopName;
        this.serialNumber = serialNumber;
        this.employeeName = employeeName;
        this.employeeId = employeeId;
        this.reason = reason;
    }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getLaptopName() { return laptopName; }
    public void setLaptopName(String laptopName) { this.laptopName = laptopName; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
