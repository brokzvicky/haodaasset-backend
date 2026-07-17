package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The Enterprise Notification Center's single source of truth. Every
 * reminder — task-based (Haoda Pulse) or module-based (warranty, service
 * billing, credential rotation, asset return, etc.) — is written here so
 * the notification bell, drawer, dashboard widget and outbound emails all
 * read from one synchronized table.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notification_is_read", columnList = "is_read"),
        @Index(name = "idx_notification_status",  columnList = "status"),
        @Index(name = "idx_notification_related", columnList = "related_module, related_record_id, notification_type"),
        @Index(name = "idx_notification_created", columnList = "created_at")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Task, Asset, Network, Billing, Security, Backup, License */
    @Column(length = 40)
    private String category;

    /** Low, Normal, High, Critical */
    @Column(length = 20, nullable = false)
    private String priority = "Normal";

    /** Pending, Sent, Actioned, Snoozed, Dismissed */
    @Column(length = 20, nullable = false)
    private String status = "Pending";

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /** Email address (or "ADMIN") this notification/email is destined for. */
    @Column(length = 160)
    private String recipient;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** When the reminder was scheduled to fire (may be in the future for snoozed items). */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** When the associated email actually went out (null until sent). */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** TASK, ASSET, NETWORK_CREDENTIAL, SERVICE_BILLING, MAINTENANCE, SYSTEM */
    @Column(name = "related_module", length = 40)
    private String relatedModule;

    @Column(name = "related_record_id", length = 40)
    private String relatedRecordId;

    /**
     * UPCOMING_TASK, DUE_TODAY, OVERDUE_TASK, HIGH_PRIORITY_TASK, TASK_ASSIGNED,
     * TASK_COMPLETED, ASSET_RETURN_REMINDER, WARRANTY_EXPIRY, LICENSE_EXPIRY,
     * SERVICE_BILLING_DUE, NETWORK_CREDENTIAL_ROTATION, FIRMWARE_UPDATE_REMINDER,
     * SECURITY_ALERT, BACKUP_REMINDER
     */
    @Column(name = "notification_type", length = 40, nullable = false)
    private String notificationType;

    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    /** The underlying due date this reminder is about (task due date, warranty expiry, etc.), for drawer display. */
    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    public java.time.LocalDate getDueDate() { return dueDate; }
    public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }

    @Column(name = "email_sent_admin", nullable = false)
    private boolean emailSentAdmin = false;

    @Column(name = "email_sent_assignee", nullable = false)
    private boolean emailSentAssignee = false;

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

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

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getRelatedModule() { return relatedModule; }
    public void setRelatedModule(String relatedModule) { this.relatedModule = relatedModule; }

    public String getRelatedRecordId() { return relatedRecordId; }
    public void setRelatedRecordId(String relatedRecordId) { this.relatedRecordId = relatedRecordId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public LocalDateTime getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(LocalDateTime snoozedUntil) { this.snoozedUntil = snoozedUntil; }

    public boolean isEmailSentAdmin() { return emailSentAdmin; }
    public void setEmailSentAdmin(boolean emailSentAdmin) { this.emailSentAdmin = emailSentAdmin; }

    public boolean isEmailSentAssignee() { return emailSentAssignee; }
    public void setEmailSentAssignee(boolean emailSentAssignee) { this.emailSentAssignee = emailSentAssignee; }
}
