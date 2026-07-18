package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per view/download of a shared file — the "Download History" log
 * shown in File Center's analytics (employee, date/time, IP, action).
 */
@Entity
@Table(
    name = "shared_file_access_logs",
    indexes = {
        @Index(name = "idx_sfal_file_id", columnList = "file_id"),
        @Index(name = "idx_sfal_accessed_at", columnList = "accessed_at")
    }
)
public class SharedFileAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "employee_id", nullable = false, length = 20)
    private String employeeId;

    @Column(name = "employee_name")
    private String employeeName;

    /** VIEW or DOWNLOAD */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getAccessedAt() { return accessedAt; }
    public void setAccessedAt(LocalDateTime accessedAt) { this.accessedAt = accessedAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
