package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String category;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String assigneeEmail;
    private LocalDate dueDate;
    private String relatedModule;
    private String relatedRecordId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public String getAssigneeEmail() { return assigneeEmail; }
    public void setAssigneeEmail(String assigneeEmail) { this.assigneeEmail = assigneeEmail; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getRelatedModule() { return relatedModule; }
    public void setRelatedModule(String relatedModule) { this.relatedModule = relatedModule; }

    public String getRelatedRecordId() { return relatedRecordId; }
    public void setRelatedRecordId(String relatedRecordId) { this.relatedRecordId = relatedRecordId; }
}
