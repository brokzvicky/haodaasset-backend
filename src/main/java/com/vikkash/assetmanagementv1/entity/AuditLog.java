package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per tracked action ("who did what, to what, and when").
 * Written by AuditLogService — never edited directly by controllers.
 * Deliberately simple (no relations) so writing an entry can never fail
 * because of a foreign-key issue on some other table.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "ASSET", "EMPLOYEE", "NETWORK_CREDENTIAL", "ASSET_REQUEST" */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    /** The affected record's business key/id, as a string (asset id, employee id, etc.) */
    @Column(name = "entity_id", length = 60)
    private String entityId;

    /** e.g. "CREATED", "UPDATED", "DELETED", "ASSIGNED", "RETURNED", "PASSWORD_REVEALED" */
    @Column(nullable = false, length = 40)
    private String action;

    /** Username / employee ID / admin email of whoever performed the action. */
    @Column(name = "performed_by", length = 100)
    private String performedBy;

    /** e.g. "ADMIN", "EMPLOYEE" — taken from their JWT role at the time of the action. */
    @Column(name = "performed_by_role", length = 30)
    private String performedByRole;

    /** Human-readable one-liner shown in the activity feed. */
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getPerformedByRole() { return performedByRole; }
    public void setPerformedByRole(String performedByRole) { this.performedByRole = performedByRole; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
