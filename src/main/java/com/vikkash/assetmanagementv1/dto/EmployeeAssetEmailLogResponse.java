package com.vikkash.assetmanagementv1.dto;

import java.time.Instant;
import java.util.List;

/** Display-ready shape for one row of the "Asset Email Logs" page. */
public class EmployeeAssetEmailLogResponse {

    private Long id;
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private List<Long> assetIds;
    private String assetsIncluded;
    private int assetCount;
    private String sentByAdmin;
    private Instant sentAt;
    private String status;
    private String errorMessage;

    public EmployeeAssetEmailLogResponse() {
    }

    public EmployeeAssetEmailLogResponse(Long id, String employeeId, String employeeName, String employeeEmail,
                                          List<Long> assetIds, String assetsIncluded, int assetCount,
                                          String sentByAdmin, Instant sentAt, String status, String errorMessage) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.assetIds = assetIds;
        this.assetsIncluded = assetsIncluded;
        this.assetCount = assetCount;
        this.sentByAdmin = sentByAdmin;
        this.sentAt = sentAt;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public List<Long> getAssetIds() { return assetIds; }
    public void setAssetIds(List<Long> assetIds) { this.assetIds = assetIds; }

    public String getAssetsIncluded() { return assetsIncluded; }
    public void setAssetsIncluded(String assetsIncluded) { this.assetsIncluded = assetsIncluded; }

    public int getAssetCount() { return assetCount; }
    public void setAssetCount(int assetCount) { this.assetCount = assetCount; }

    public String getSentByAdmin() { return sentByAdmin; }
    public void setSentByAdmin(String sentByAdmin) { this.sentByAdmin = sentByAdmin; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
