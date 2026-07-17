package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

public class CustomNotificationRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "notificationType is required")
    private String notificationType; // e.g. SECURITY_ALERT, BACKUP_REMINDER, LICENSE_EXPIRY

    private String category;
    private String priority;
    private String relatedModule;
    private String relatedRecordId;
    private String recipientEmail; // optional extra recipient besides admin

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getRelatedModule() { return relatedModule; }
    public void setRelatedModule(String relatedModule) { this.relatedModule = relatedModule; }

    public String getRelatedRecordId() { return relatedRecordId; }
    public void setRelatedRecordId(String relatedRecordId) { this.relatedRecordId = relatedRecordId; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
}
