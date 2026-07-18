package com.vikkash.assetmanagementv1.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the admin File Center list — file metadata plus its download/read stats. */
public class SharedFileSummaryDTO {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String tags;
    private String version;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
    private LocalDate expiryDate;
    private boolean expired;
    private String recipientType;
    private String recipientSummary;

    private long sharedTo;
    private long downloaded;
    private long viewed;
    private long unread;
    private long pending;
    private LocalDateTime lastDownload;

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

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public String getRecipientSummary() { return recipientSummary; }
    public void setRecipientSummary(String recipientSummary) { this.recipientSummary = recipientSummary; }

    public long getSharedTo() { return sharedTo; }
    public void setSharedTo(long sharedTo) { this.sharedTo = sharedTo; }

    public long getDownloaded() { return downloaded; }
    public void setDownloaded(long downloaded) { this.downloaded = downloaded; }

    public long getViewed() { return viewed; }
    public void setViewed(long viewed) { this.viewed = viewed; }

    public long getUnread() { return unread; }
    public void setUnread(long unread) { this.unread = unread; }

    public long getPending() { return pending; }
    public void setPending(long pending) { this.pending = pending; }

    public LocalDateTime getLastDownload() { return lastDownload; }
    public void setLastDownload(LocalDateTime lastDownload) { this.lastDownload = lastDownload; }
}
