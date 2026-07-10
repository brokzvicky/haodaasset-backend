package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Audit record of every "Send Asset Email" attempt from the enterprise
 * bulk-send admin page (Admin searches an employee, selects one or more of
 * their assigned assets, and emails them all in a single message).
 *
 * This is intentionally a separate table from {@link AssetEmailLog}, which
 * only ever covers a single asset per row (the per-asset "Resend" flow on
 * the Assets page). Here one row = one email = one employee + N assets, so
 * assetIds/assetsSummary are stored as delimited text rather than a single
 * asset id.
 *
 * One row per attempt — "Resend" creates a new row rather than overwriting
 * the previous one, so the full history is preserved.
 */
@Entity
@Table(
    name = "employee_asset_email_logs",
    indexes = {
        @Index(name = "idx_emp_asset_email_log_employee_id", columnList = "employeeId"),
        @Index(name = "idx_emp_asset_email_log_sent_at",     columnList = "sentAt")
    }
)
public class EmployeeAssetEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "employee_name")
    private String employeeName;

    @Column(name = "employee_email")
    private String employeeEmail;

    /** Comma-separated asset IDs actually included in the email, e.g. "12,15,20". Used to re-resolve current asset data on Resend. */
    @Column(name = "asset_ids", columnDefinition = "TEXT")
    private String assetIds;

    /** Human-readable summary for the "Assets Included" column, e.g. "Dell Latitude 5420 (SN123), HP EliteBook 840 (SN456)". */
    @Column(name = "assets_summary", columnDefinition = "TEXT")
    private String assetsSummary;

    @Column(name = "asset_count", nullable = false)
    private int assetCount;

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

    public EmployeeAssetEmailLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getAssetIds() { return assetIds; }
    public void setAssetIds(String assetIds) { this.assetIds = assetIds; }

    public String getAssetsSummary() { return assetsSummary; }
    public void setAssetsSummary(String assetsSummary) { this.assetsSummary = assetsSummary; }

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
