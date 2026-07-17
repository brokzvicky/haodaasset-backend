package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A Haoda Pulse task — the work-tracking unit that drives the Enterprise
 * Notification Center's task-based reminders (Upcoming, Due Today,
 * Overdue, High Priority, Assigned, Completed).
 *
 * A task can stand alone (general to-do) or be linked to another module
 * record (relatedModule/relatedRecordId), e.g. "renew warranty on Asset
 * #42" or "rotate credentials on Network Device #7".
 */
@Entity
@Table(
    name = "pulse_tasks",
    indexes = {
        @Index(name = "idx_pulse_task_status",   columnList = "status"),
        @Index(name = "idx_pulse_task_due",      columnList = "due_date"),
        @Index(name = "idx_pulse_task_assignee", columnList = "assignee_id")
    }
)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** General, Asset, Employee, NetworkCredential, ServiceBilling, Maintenance, Security, Backup */
    @Column(length = 40)
    private String category = "General";

    /** Low, Normal, High, Critical */
    @Column(length = 20, nullable = false)
    private String priority = "Normal";

    /** Pending, InProgress, Completed, Cancelled */
    @Column(length = 20, nullable = false)
    private String status = "Pending";

    @Column(name = "assignee_id", length = 20)
    private String assigneeId;

    @Column(name = "assignee_name", length = 120)
    private String assigneeName;

    @Column(name = "assignee_email", length = 160)
    private String assigneeEmail;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** ASSET, EMPLOYEE, NETWORK_CREDENTIAL, SERVICE_BILLING, MAINTENANCE, null for standalone tasks */
    @Column(name = "related_module", length = 40)
    private String relatedModule;

    @Column(name = "related_record_id", length = 40)
    private String relatedRecordId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public String getAssigneeEmail() { return assigneeEmail; }
    public void setAssigneeEmail(String assigneeEmail) { this.assigneeEmail = assigneeEmail; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getRelatedModule() { return relatedModule; }
    public void setRelatedModule(String relatedModule) { this.relatedModule = relatedModule; }

    public String getRelatedRecordId() { return relatedRecordId; }
    public void setRelatedRecordId(String relatedRecordId) { this.relatedRecordId = relatedRecordId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
