package com.vikkash.assetmanagementv1.dto;

import java.time.Instant;

/**
 * Flattened, display-ready shape for one row of the Email Logs page.
 * Combines the log entry with the asset's current name/serial so the UI
 * doesn't need a second lookup — and still shows a sensible label even if
 * the asset was later deleted (assetLabel falls back to "Asset #<id>").
 */
public class AssetEmailLogResponse {

    private Long id;
    private Long assetId;
    private String assetLabel;
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private String sentByAdmin;
    private Instant sentAt;
    private String status;
    private String errorMessage;
    private String emailType;

    public AssetEmailLogResponse() {
    }

    public AssetEmailLogResponse(Long id, Long assetId, String assetLabel, String employeeId,
                                  String employeeName, String employeeEmail, String sentByAdmin,
                                  Instant sentAt, String status, String errorMessage, String emailType) {
        this.id = id;
        this.assetId = assetId;
        this.assetLabel = assetLabel;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.sentByAdmin = sentByAdmin;
        this.sentAt = sentAt;
        this.status = status;
        this.errorMessage = errorMessage;
        this.emailType = emailType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getAssetLabel() { return assetLabel; }
    public void setAssetLabel(String assetLabel) { this.assetLabel = assetLabel; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getSentByAdmin() { return sentByAdmin; }
    public void setSentByAdmin(String sentByAdmin) { this.sentByAdmin = sentByAdmin; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getEmailType() { return emailType; }
    public void setEmailType(String emailType) { this.emailType = emailType; }
}
