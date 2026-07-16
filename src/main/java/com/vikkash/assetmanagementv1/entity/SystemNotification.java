package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A persisted, dismissible system notification shown in the admin
 * notification bell — distinct from AuditLog (which is an immutable
 * "who did what" trail). Generated automatically by
 * NotificationGeneratorService for things that need admin attention
 * (warranty expiring soon, maintenance due, temporary assignment expired),
 * and deduplicated so the same event doesn't spam the bell every day.
 */
@Entity
@Table(
    name = "system_notifications",
    indexes = {
        @Index(name = "idx_notif_is_read", columnList = "is_read"),
        @Index(name = "idx_notif_related", columnList = "related_type, related_id, type")
    }
)
public class SystemNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "WARRANTY_EXPIRING", "MAINTENANCE_DUE", "TEMP_ASSIGNMENT_EXPIRED", "ASSET_REQUEST" */
    @Column(nullable = false, length = 40)
    private String type;

    /** "info", "warning", "critical" — drives the icon/colour on the frontend. */
    @Column(length = 20)
    private String severity = "info";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id", length = 40)
    private String relatedId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }

    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
