package com.vikkash.assetmanagementv1.dto;

import java.time.LocalDateTime;

/**
 * One entry in an asset's unified Timeline — merges AuditLog entries,
 * email-send history, and maintenance events into a single chronological
 * feed for the Asset Timeline UI.
 */
public class TimelineEventDTO {

    /** "AUDIT", "EMAIL", "MAINTENANCE", "DOCUMENT" */
    private String source;
    private String action;
    private String description;
    private String performedBy;
    private LocalDateTime timestamp;

    public TimelineEventDTO() {}

    public TimelineEventDTO(String source, String action, String description, String performedBy, LocalDateTime timestamp) {
        this.source = source;
        this.action = action;
        this.description = description;
        this.performedBy = performedBy;
        this.timestamp = timestamp;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
