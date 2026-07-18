package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Haoda File Center: a file an admin has distributed to one or more
 * employees (VPN guides, policies, installers, drivers, etc.).
 *
 * The storage/version fields on this row always describe the CURRENT
 * (latest) version — {@link SharedFileVersion} keeps the full history.
 * This mirrors the pattern used for asset documents: files live on disk
 * under a random, collision-proof name; only metadata is in the DB.
 */
@Entity
@Table(
    name = "shared_files",
    indexes = {
        @Index(name = "idx_shared_file_category", columnList = "category"),
        @Index(name = "idx_shared_file_uploaded_at", columnList = "uploaded_at")
    }
)
public class SharedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    /** IT Documents, HR Documents, Software, Drivers, Training Material, Policies, Network Documents, Forms, Security, Other */
    @Column(nullable = false, length = 40)
    private String category = "Other";

    /** Low, Normal, High, Critical */
    @Column(nullable = false, length = 20)
    private String priority = "Normal";

    /** Comma-separated tags, e.g. "vpn,network,remote-access" */
    @Column(length = 500)
    private String tags;

    /** Current version label, e.g. "v1.0", "v2.1" */
    @Column(nullable = false, length = 20)
    private String version = "v1.0";

    // ── Current version's stored file (see SharedFileVersion for history) ──
    @Column(name = "stored_file_name", nullable = false, length = 120)
    private String storedFileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Optional — file is still shown/downloadable after this date, but flagged as expired in the UI. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** ALL, DEPARTMENT, LOCATION, INDIVIDUAL, MULTIPLE, ASSET_OWNERS — how recipients were last selected. */
    @Column(name = "recipient_type", length = 30)
    private String recipientType;

    /** Human-readable recipient selection, e.g. "All Employees", "Engineering, Sales", cached so the list view doesn't recompute it. */
    @Column(name = "recipient_summary", length = 300)
    private String recipientSummary;

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

    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

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

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public String getRecipientSummary() { return recipientSummary; }
    public void setRecipientSummary(String recipientSummary) { this.recipientSummary = recipientSummary; }
}
