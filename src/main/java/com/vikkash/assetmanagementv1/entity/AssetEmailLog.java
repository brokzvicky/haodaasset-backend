package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Audit record of every "Asset Assignment" notification email attempt
 * (send or resend). One row per attempt — a resend creates a new row
 * rather than overwriting the previous one, so the full history is kept.
 */
@Entity
@Table(
    name = "asset_email_logs",
    indexes = {
        @Index(name = "idx_email_log_asset_id", columnList = "assetId"),
        @Index(name = "idx_email_log_sent_at",  columnList = "sentAt")
    }
)
public class AssetEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "employee_email")
    private String employeeEmail;

    /**
     * One of: ASSIGNMENT, RETURN. Defaults to ASSIGNMENT so existing rows
     * (written before this column existed) still read back correctly.
     */
    @Column(name = "email_type", length = 20)
    private String emailType = "ASSIGNMENT";

    /** Admin username who triggered the send (from JWT subject). */
    @Column(name = "sent_by_admin")
    private String sentByAdmin;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    /** One of: SENT, FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public AssetEmailLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getEmailType() { return emailType; }
    public void setEmailType(String emailType) { this.emailType = emailType; }

    public String getSentByAdmin() { return sentByAdmin; }
    public void setSentByAdmin(String sentByAdmin) { this.sentByAdmin = sentByAdmin; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
