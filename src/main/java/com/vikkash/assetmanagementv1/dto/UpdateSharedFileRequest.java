package com.vikkash.assetmanagementv1.dto;

import java.time.LocalDate;

/** Request body for PUT /api/admin/filecenter/files/{id} — metadata-only edit. */
public class UpdateSharedFileRequest {

    private String title;
    private String description;
    private String category;
    private String priority;
    private String tags;
    private LocalDate expiryDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
