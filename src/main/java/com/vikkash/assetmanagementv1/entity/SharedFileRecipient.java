package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Maps a {@link SharedFile} to one employee recipient, and tracks that
 * employee's read status for it. This is the row "My Files" reads for an
 * employee, and doubles as the File Center's in-app notification/unread
 * signal — deliberately kept separate from the Enterprise Notification
 * Center (Haoda Pulse) table so File Center activity never clutters the
 * admin's existing Pulse bell.
 */
@Entity
@Table(
    name = "shared_file_recipients",
    indexes = {
        @Index(name = "idx_sfr_file_id", columnList = "file_id"),
        @Index(name = "idx_sfr_employee_id", columnList = "employee_id")
    },
    uniqueConstraints = { @UniqueConstraint(name = "uk_sfr_file_employee", columnNames = { "file_id", "employee_id" }) }
)
public class SharedFileRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "employee_id", nullable = false, length = 20)
    private String employeeId;

    // Denormalized so recipient/download-history rows still read meaningfully
    // even if the employee's own record is later edited or deleted.
    @Column(name = "employee_name")
    private String employeeName;

    @Column(name = "employee_email")
    private String employeeEmail;

    @Column(name = "department")
    private String department;

    @Column(name = "location")
    private String location;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent = false;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "shared_at", nullable = false)
    private LocalDateTime sharedAt = LocalDateTime.now();

    /** Bumped every time a newer version is pushed and this recipient is re-notified. */
    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }

    public LocalDateTime getEmailSentAt() { return emailSentAt; }
    public void setEmailSentAt(LocalDateTime emailSentAt) { this.emailSentAt = emailSentAt; }

    public LocalDateTime getSharedAt() { return sharedAt; }
    public void setSharedAt(LocalDateTime sharedAt) { this.sharedAt = sharedAt; }

    public LocalDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(LocalDateTime lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
}
