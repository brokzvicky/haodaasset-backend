package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single maintenance event (preventive, corrective, or inspection) tracked
 * against an asset. Multiple records accumulate over an asset's lifetime,
 * forming its maintenance history.
 */
@Entity
@Table(
    name = "maintenance_records",
    indexes = {
        @Index(name = "idx_maintenance_asset_id", columnList = "asset_id"),
        @Index(name = "idx_maintenance_status", columnList = "status")
    }
)
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** "Preventive", "Corrective", "Inspection", "Upgrade" */
    @Column(name = "maintenance_type", length = 40)
    private String maintenanceType = "Preventive";

    @Column(length = 1000)
    private String description;

    /** "Scheduled", "In Progress", "Completed", "Cancelled" */
    @Column(length = 30)
    private String status = "Scheduled";

    @Column(name = "scheduled_date")
    private String scheduledDate;

    @Column(name = "completed_date")
    private String completedDate;

    private String vendor;

    @Column(name = "cost")
    private String cost;

    @Column(name = "performed_by")
    private String performedBy;

    /** Next due date for the following maintenance cycle (drives reminders). */
    @Column(name = "next_maintenance_date")
    private String nextMaintenanceDate;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getCompletedDate() { return completedDate; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getNextMaintenanceDate() { return nextMaintenanceDate; }
    public void setNextMaintenanceDate(String nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
