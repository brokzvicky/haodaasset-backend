package com.vikkash.assetmanagementv1.dto;

/**
 * Summarizes one asset that was repaired by the orphaned-assignment
 * cleanup. "Repair" means: the asset's broken employeeId link was
 * detected (see OrphanedAssetDTO), so its assignment fields were
 * cleared and its status reset to Available — freeing it up to be
 * properly re-assigned through the normal Assign Asset flow.
 *
 * This makes no attempt to guess the correct employee; it only undoes
 * the broken assignment so an admin can redo it correctly.
 */
public class RepairResultDTO {

    private Long assetId;
    private String laptopName;
    private String serialNumber;
    private String previousEmployeeName;
    private String previousEmployeeId;
    private String reason;
    private String newAssetStatus;

    public RepairResultDTO() {
    }

    public RepairResultDTO(Long assetId, String laptopName, String serialNumber,
                            String previousEmployeeName, String previousEmployeeId,
                            String reason, String newAssetStatus) {
        this.assetId = assetId;
        this.laptopName = laptopName;
        this.serialNumber = serialNumber;
        this.previousEmployeeName = previousEmployeeName;
        this.previousEmployeeId = previousEmployeeId;
        this.reason = reason;
        this.newAssetStatus = newAssetStatus;
    }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getLaptopName() { return laptopName; }
    public void setLaptopName(String laptopName) { this.laptopName = laptopName; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getPreviousEmployeeName() { return previousEmployeeName; }
    public void setPreviousEmployeeName(String previousEmployeeName) { this.previousEmployeeName = previousEmployeeName; }

    public String getPreviousEmployeeId() { return previousEmployeeId; }
    public void setPreviousEmployeeId(String previousEmployeeId) { this.previousEmployeeId = previousEmployeeId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNewAssetStatus() { return newAssetStatus; }
    public void setNewAssetStatus(String newAssetStatus) { this.newAssetStatus = newAssetStatus; }
}
