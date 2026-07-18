package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.*;
import com.vikkash.assetmanagementv1.entity.*;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Haoda File Center: lets admins securely distribute files (guides,
 * policies, installers, drivers) to employees without emailing them one by
 * one. Files live on disk (same pattern as {@link AssetDocumentService});
 * only metadata + recipient/read/access-log rows are in the DB.
 *
 * Recipients never receive the file as an email attachment — the
 * notification email links back into File Center, so every open/download
 * goes through {@link #recordAccess} and is logged.
 */
@Service
public class SharedFileService {

    private static final Logger log = LoggerFactory.getLogger(SharedFileService.class);

    private static final List<String> CATEGORIES = List.of(
            "IT Documents", "HR Documents", "Software", "Drivers", "Training Material",
            "Policies", "Network Documents", "Forms", "Security", "Other");

    private final SharedFileRepository fileRepository;
    private final SharedFileVersionRepository versionRepository;
    private final SharedFileRecipientRepository recipientRepository;
    private final SharedFileAccessLogRepository accessLogRepository;
    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${app.storage.file-center-upload-dir:uploads/file-center}")
    private String uploadDir;

    @Value("${app.filecenter.max-upload-mb:100}")
    private long maxUploadMb;

    @Value("${app.frontend.base-url:https://haodaasset.vercel.app}")
    private String frontendBaseUrl;

    public SharedFileService(SharedFileRepository fileRepository,
                              SharedFileVersionRepository versionRepository,
                              SharedFileRecipientRepository recipientRepository,
                              SharedFileAccessLogRepository accessLogRepository,
                              EmployeeRepository employeeRepository,
                              AssetRepository assetRepository,
                              EmailService emailService,
                              AuditLogService auditLogService) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.recipientRepository = recipientRepository;
        this.accessLogRepository = accessLogRepository;
        this.employeeRepository = employeeRepository;
        this.assetRepository = assetRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    public List<String> getCategories() {
        return CATEGORIES;
    }

    public long getMaxUploadMb() {
        return maxUploadMb;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Recipient resolution
    // ══════════════════════════════════════════════════════════════════════

    /** Resolves a recipient selection into a concrete, deduplicated employee list. */
    @Transactional(readOnly = true)
    public List<Employee> resolveRecipients(String recipientType, List<String> values) {
        List<Employee> all = employeeRepository.findAll();
        Set<String> wanted = values == null ? Set.of() :
                values.stream().filter(Objects::nonNull).map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());

        return switch (nullToAll(recipientType)) {
            case "ALL" -> all;
            case "DEPARTMENT" -> all.stream()
                    .filter(e -> e.getDepartment() != null && wanted.contains(e.getDepartment().trim().toLowerCase()))
                    .toList();
            case "LOCATION" -> all.stream()
                    .filter(e -> e.getLocation() != null && wanted.contains(e.getLocation().trim().toLowerCase()))
                    .toList();
            case "INDIVIDUAL", "MULTIPLE" -> all.stream()
                    .filter(e -> wanted.contains(e.getEmployeeId().trim().toLowerCase()))
                    .toList();
            case "ASSET_OWNERS" -> {
                Set<String> ownerIds = assetRepository.findAll().stream()
                        .map(Asset::getEmployeeId)
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
                yield all.stream().filter(e -> ownerIds.contains(e.getEmployeeId().toUpperCase())).toList();
            }
            default -> throw new IllegalArgumentException("Unknown recipientType: " + recipientType);
        };
    }

    private String nullToAll(String type) {
        return (type == null || type.isBlank()) ? "ALL" : type.trim().toUpperCase();
    }

    private String summarize(String recipientType, List<String> values, List<Employee> resolved) {
        String type = nullToAll(recipientType);
        return switch (type) {
            case "ALL" -> "All Employees";
            case "ASSET_OWNERS" -> "Asset Owners";
            case "DEPARTMENT" -> values == null ? "Department" : String.join(", ", values);
            case "LOCATION" -> values == null ? "Location" : String.join(", ", values);
            case "INDIVIDUAL" -> resolved.size() == 1 ? resolved.get(0).getEmployeeName() : resolved.size() + " Employees";
            case "MULTIPLE" -> resolved.size() + " Employees";
            default -> resolved.size() + " Employees";
        };
    }

    /** Powers the Share File confirmation modal's "145 Employees" preview, before anything is uploaded. */
    @Transactional(readOnly = true)
    public RecipientPreviewResponse previewRecipients(String recipientType, List<String> values) {
        List<Employee> resolved = resolveRecipients(recipientType, values);
        String summary = summarize(recipientType, values, resolved);
        List<String> sample = resolved.stream().limit(5).map(Employee::getEmployeeName).toList();
        return new RecipientPreviewResponse(resolved.size(), summary, sample);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Share (upload) a new file
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public SharedFile shareFile(MultipartFile file, String title, String description, String category,
                                 String priority, String tags, LocalDate expiryDate,
                                 String recipientType, List<String> recipientValues,
                                 boolean sendEmail, String emailSubject, String emailMessage,
                                 String uploadedBy) {
        validateFile(file);

        SharedFile shared = new SharedFile();
        shared.setTitle(title);
        shared.setDescription(description);
        shared.setCategory((category == null || category.isBlank()) ? "Other" : category);
        shared.setPriority((priority == null || priority.isBlank()) ? "Normal" : priority);
        shared.setTags(tags);
        shared.setVersion("v1.0");
        shared.setExpiryDate(expiryDate);
        shared.setUploadedBy(uploadedBy);
        shared.setUploadedAt(LocalDateTime.now());

        StoredFile stored = storeOnDisk(file);
        shared.setStoredFileName(stored.storedFileName());
        shared.setOriginalFileName(stored.originalFileName());
        shared.setContentType(stored.contentType());
        shared.setFileSize(stored.fileSize());

        List<Employee> recipients = resolveRecipients(recipientType, recipientValues);
        shared.setRecipientType(nullToAll(recipientType));
        shared.setRecipientSummary(summarize(recipientType, recipientValues, recipients));

        SharedFile saved = fileRepository.save(shared);

        writeVersionSnapshot(saved, "v1.0", stored, uploadedBy, null);
        addRecipients(saved, recipients);

        if (sendEmail) {
            notifyRecipients(saved, recipients, emailSubject, emailMessage);
        }

        auditLogService.record("FILE_CENTER", String.valueOf(saved.getId()), "FILE_SHARED",
                "Shared '" + saved.getTitle() + "' with " + recipients.size() + " employee(s)", uploadedBy);

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Replace with a newer version
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public SharedFile replaceVersion(Long fileId, MultipartFile file, String newVersion, String changeNotes,
                                      String recipientType, List<String> additionalRecipientValues,
                                      boolean sendEmail, String emailSubject, String emailMessage,
                                      String uploadedBy) {
        SharedFile shared = getEntity(fileId);
        validateFile(file);

        StoredFile stored = storeOnDisk(file);
        String version = (newVersion == null || newVersion.isBlank()) ? nextVersionGuess(shared.getVersion()) : newVersion.trim();

        shared.setVersion(version);
        shared.setStoredFileName(stored.storedFileName());
        shared.setOriginalFileName(stored.originalFileName());
        shared.setContentType(stored.contentType());
        shared.setFileSize(stored.fileSize());
        shared.setUpdatedAt(LocalDateTime.now());
        fileRepository.save(shared);

        writeVersionSnapshot(shared, version, stored, uploadedBy, changeNotes);

        // Existing recipients always get the latest version — just flip them back to unread
        // so the new version surfaces in "My Files" again.
        List<SharedFileRecipient> existing = recipientRepository.findByFileIdOrderBySharedAtDesc(fileId);
        LocalDateTime now = LocalDateTime.now();
        for (SharedFileRecipient r : existing) {
            r.setRead(false);
            r.setReadAt(null);
            r.setLastNotifiedAt(now);
        }
        recipientRepository.saveAll(existing);

        // Optionally widen the audience at the same time.
        List<Employee> newlyAdded = List.of();
        if (recipientType != null && !recipientType.isBlank()) {
            List<Employee> resolved = resolveRecipients(recipientType, additionalRecipientValues);
            Set<String> existingIds = existing.stream().map(SharedFileRecipient::getEmployeeId).collect(Collectors.toSet());
            newlyAdded = resolved.stream().filter(e -> !existingIds.contains(e.getEmployeeId())).toList();
            addRecipients(shared, newlyAdded);
        }

        if (sendEmail) {
            List<Employee> allRecipientEmployees = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (SharedFileRecipient r : recipientRepository.findByFileIdOrderBySharedAtDesc(fileId)) {
                if (seen.add(r.getEmployeeId())) {
                    employeeRepository.findByEmployeeId(r.getEmployeeId()).ifPresent(allRecipientEmployees::add);
                }
            }
            notifyRecipients(shared, allRecipientEmployees, emailSubject, emailMessage);
        }

        auditLogService.record("FILE_CENTER", String.valueOf(shared.getId()), "FILE_VERSION_REPLACED",
                "Replaced '" + shared.getTitle() + "' with version " + version
                        + (newlyAdded.isEmpty() ? "" : " (+" + newlyAdded.size() + " new recipient(s))"), uploadedBy);

        return shared;
    }

    private String nextVersionGuess(String current) {
        try {
            String numeric = current.replaceAll("(?i)^v", "");
            double v = Double.parseDouble(numeric);
            return "v" + (Math.round((v + 0.1) * 10) / 10.0);
        } catch (Exception e) {
            return current + "+";
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Metadata edit / delete
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public SharedFile updateMetadata(Long fileId, UpdateSharedFileRequest req, String updatedBy) {
        SharedFile shared = getEntity(fileId);
        if (req.getTitle() != null) shared.setTitle(req.getTitle());
        if (req.getDescription() != null) shared.setDescription(req.getDescription());
        if (req.getCategory() != null) shared.setCategory(req.getCategory());
        if (req.getPriority() != null) shared.setPriority(req.getPriority());
        if (req.getTags() != null) shared.setTags(req.getTags());
        shared.setExpiryDate(req.getExpiryDate());
        shared.setUpdatedAt(LocalDateTime.now());
        SharedFile saved = fileRepository.save(shared);
        auditLogService.record("FILE_CENTER", String.valueOf(fileId), "FILE_UPDATED",
                "Updated metadata for '" + saved.getTitle() + "'", updatedBy);
        return saved;
    }

    @Transactional
    public void deleteFile(Long fileId, String deletedBy) {
        SharedFile shared = getEntity(fileId);

        // Remove the physical files for every version, then the DB rows.
        for (SharedFileVersion v : versionRepository.findByFileIdOrderByUploadedAtDesc(fileId)) {
            deleteFromDisk(v.getStoredFileName());
        }
        accessLogRepository.deleteByFileId(fileId);
        recipientRepository.deleteByFileId(fileId);
        versionRepository.deleteByFileId(fileId);
        fileRepository.delete(shared);

        auditLogService.record("FILE_CENTER", String.valueOf(fileId), "FILE_DELETED",
                "Deleted '" + shared.getTitle() + "'", deletedBy);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Admin reads
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<SharedFileSummaryDTO> listFiles(String search, String category, String priority, String uploadedBy) {
        String q = search == null ? null : search.trim().toLowerCase();
        return fileRepository.findAllByOrderByUploadedAtDesc().stream()
                .filter(f -> category == null || category.isBlank() || category.equalsIgnoreCase(f.getCategory()))
                .filter(f -> priority == null || priority.isBlank() || priority.equalsIgnoreCase(f.getPriority()))
                .filter(f -> uploadedBy == null || uploadedBy.isBlank()
                        || (f.getUploadedBy() != null && f.getUploadedBy().toLowerCase().contains(uploadedBy.toLowerCase())))
                .filter(f -> q == null || q.isBlank() || matchesSearch(f, q))
                .map(this::toSummary)
                .toList();
    }

    private boolean matchesSearch(SharedFile f, String q) {
        return containsIgnoreCase(f.getTitle(), q) || containsIgnoreCase(f.getCategory(), q)
                || containsIgnoreCase(f.getTags(), q) || containsIgnoreCase(f.getUploadedBy(), q)
                || containsIgnoreCase(f.getDescription(), q);
    }

    private boolean containsIgnoreCase(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    @Transactional(readOnly = true)
    public SharedFileDetailDTO getDetail(Long fileId) {
        SharedFile shared = getEntity(fileId);
        List<SharedFileRecipient> recipients = recipientRepository.findByFileIdOrderBySharedAtDesc(fileId);
        List<SharedFileVersion> versions = versionRepository.findByFileIdOrderByUploadedAtDesc(fileId);
        List<SharedFileAccessLog> logs = accessLogRepository.findByFileIdOrderByAccessedAtDesc(fileId);
        return new SharedFileDetailDTO(toSummary(shared), recipients, versions, logs);
    }

    private SharedFileSummaryDTO toSummary(SharedFile f) {
        SharedFileSummaryDTO dto = new SharedFileSummaryDTO();
        dto.setId(f.getId());
        dto.setTitle(f.getTitle());
        dto.setDescription(f.getDescription());
        dto.setCategory(f.getCategory());
        dto.setPriority(f.getPriority());
        dto.setTags(f.getTags());
        dto.setVersion(f.getVersion());
        dto.setOriginalFileName(f.getOriginalFileName());
        dto.setContentType(f.getContentType());
        dto.setFileSize(f.getFileSize());
        dto.setUploadedBy(f.getUploadedBy());
        dto.setUploadedAt(f.getUploadedAt());
        dto.setUpdatedAt(f.getUpdatedAt());
        dto.setExpiryDate(f.getExpiryDate());
        dto.setExpired(f.getExpiryDate() != null && f.getExpiryDate().isBefore(LocalDate.now()));
        dto.setRecipientType(f.getRecipientType());
        dto.setRecipientSummary(f.getRecipientSummary());

        long sharedTo = recipientRepository.countByFileId(f.getId());
        long readCount = recipientRepository.countByFileIdAndReadTrue(f.getId());
        long downloaded = accessLogRepository.countByFileIdAndAction(f.getId(), "DOWNLOAD");
        long viewed = accessLogRepository.countByFileIdAndAction(f.getId(), "VIEW");
        SharedFileAccessLog lastDownloadLog = accessLogRepository.findFirstByFileIdAndActionOrderByAccessedAtDesc(f.getId(), "DOWNLOAD");

        dto.setSharedTo(sharedTo);
        dto.setDownloaded(downloaded);
        dto.setViewed(viewed);
        dto.setUnread(sharedTo - readCount);
        dto.setPending(sharedTo - Math.min(sharedTo, downloaded));
        dto.setLastDownload(lastDownloadLog != null ? lastDownloadLog.getAccessedAt() : null);
        return dto;
    }

    @Transactional(readOnly = true)
    public SharedFile getEntity(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));
    }

    @Transactional(readOnly = true)
    public Path resolveFileForAdmin(Long fileId) {
        SharedFile shared = getEntity(fileId);
        return resolveOnDisk(shared.getStoredFileName());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Employee-facing reads/actions
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<MyFileDTO> myFiles(String employeeId) {
        List<SharedFileRecipient> mine = recipientRepository.findByEmployeeIdOrderBySharedAtDesc(employeeId);
        List<MyFileDTO> result = new ArrayList<>();
        for (SharedFileRecipient r : mine) {
            fileRepository.findById(r.getFileId()).ifPresent(f -> result.add(toMyFileDTO(f, r)));
        }
        return result;
    }

    private MyFileDTO toMyFileDTO(SharedFile f, SharedFileRecipient r) {
        MyFileDTO dto = new MyFileDTO();
        dto.setFileId(f.getId());
        dto.setTitle(f.getTitle());
        dto.setDescription(f.getDescription());
        dto.setCategory(f.getCategory());
        dto.setPriority(f.getPriority());
        dto.setTags(f.getTags());
        dto.setVersion(f.getVersion());
        dto.setOriginalFileName(f.getOriginalFileName());
        dto.setContentType(f.getContentType());
        dto.setFileSize(f.getFileSize());
        dto.setUploadedBy(f.getUploadedBy());
        dto.setSharedAt(r.getLastNotifiedAt() != null && r.getLastNotifiedAt().isAfter(r.getSharedAt()) ? r.getLastNotifiedAt() : r.getSharedAt());
        dto.setExpiryDate(f.getExpiryDate());
        dto.setExpired(f.getExpiryDate() != null && f.getExpiryDate().isBefore(LocalDate.now()));
        dto.setRead(r.isRead());
        dto.setReadAt(r.getReadAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public long unreadCount(String employeeId) {
        return recipientRepository.countByEmployeeIdAndReadFalse(employeeId);
    }

    private SharedFileRecipient requireRecipient(Long fileId, String employeeId) {
        return recipientRepository.findByFileIdAndEmployeeId(fileId, employeeId)
                .orElseThrow(() -> new AccessDeniedException("This file wasn't shared with you."));
    }

    @Transactional
    public void markRead(Long fileId, String employeeId) {
        SharedFileRecipient r = requireRecipient(fileId, employeeId);
        r.setRead(true);
        r.setReadAt(LocalDateTime.now());
        recipientRepository.save(r);
    }

    /** Verifies access, logs a VIEW/DOWNLOAD, marks read, and returns the file path to stream. */
    @Transactional
    public Path recordAccess(Long fileId, String employeeId, String action, String ipAddress) {
        SharedFile shared = getEntity(fileId);
        SharedFileRecipient recipient = requireRecipient(fileId, employeeId);

        SharedFileAccessLog logEntry = new SharedFileAccessLog();
        logEntry.setFileId(fileId);
        logEntry.setEmployeeId(employeeId);
        logEntry.setEmployeeName(recipient.getEmployeeName());
        logEntry.setAction(action);
        logEntry.setIpAddress(ipAddress);
        accessLogRepository.save(logEntry);

        if (!recipient.isRead()) {
            recipient.setRead(true);
            recipient.setReadAt(LocalDateTime.now());
            recipientRepository.save(recipient);
        }

        return resolveOnDisk(shared.getStoredFileName());
    }

    @Transactional(readOnly = true)
    public SharedFile getEntityForEmployeeDownload(Long fileId, String employeeId) {
        requireRecipient(fileId, employeeId);
        return getEntity(fileId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════

    private void addRecipients(SharedFile shared, List<Employee> employees) {
        LocalDateTime now = LocalDateTime.now();
        for (Employee e : employees) {
            if (recipientRepository.findByFileIdAndEmployeeId(shared.getId(), e.getEmployeeId()).isPresent()) continue;
            SharedFileRecipient r = new SharedFileRecipient();
            r.setFileId(shared.getId());
            r.setEmployeeId(e.getEmployeeId());
            r.setEmployeeName(e.getEmployeeName());
            r.setEmployeeEmail(e.getEmail());
            r.setDepartment(e.getDepartment());
            r.setLocation(e.getLocation());
            r.setSharedAt(now);
            recipientRepository.save(r);
        }
    }

    private void notifyRecipients(SharedFile shared, List<Employee> employees, String subjectOverride, String messageOverride) {
        String subject = (subjectOverride == null || subjectOverride.isBlank())
                ? "New File Available — Haoda File Center" : subjectOverride;
        String intro = (messageOverride == null || messageOverride.isBlank())
                ? "A new file has been shared with you by the IT Team. Please click the button below to securely access the file."
                : messageOverride;
        String viewUrl = frontendBaseUrl + "/emp/files?fileId=" + shared.getId();

        for (Employee e : employees) {
            if (e.getEmail() == null || e.getEmail().isBlank()) continue;
            try {
                emailService.sendFileSharedEmail(e.getEmail(), e.getEmployeeName(), subject, intro,
                        new EmailService.FileSharedEmailDetails(
                                shared.getTitle(), shared.getCategory(), shared.getPriority(),
                                shared.getUploadedBy(), shared.getVersion(), viewUrl));
                recipientRepository.findByFileIdAndEmployeeId(shared.getId(), e.getEmployeeId()).ifPresent(r -> {
                    r.setEmailSent(true);
                    r.setEmailSentAt(LocalDateTime.now());
                    recipientRepository.save(r);
                });
            } catch (Exception ex) {
                // One employee's bad/blocked address shouldn't stop the rest of the batch.
                log.warn("Couldn't send File Center email to {} for file {}: {}", e.getEmployeeId(), shared.getId(), ex.getMessage());
            }
        }
    }

    private void writeVersionSnapshot(SharedFile shared, String version, StoredFile stored, String uploadedBy, String changeNotes) {
        versionRepository.findByFileIdAndCurrentTrue(shared.getId()).ifPresent(prev -> {
            prev.setCurrent(false);
            versionRepository.save(prev);
        });
        SharedFileVersion v = new SharedFileVersion();
        v.setFileId(shared.getId());
        v.setVersion(version);
        v.setStoredFileName(stored.storedFileName());
        v.setOriginalFileName(stored.originalFileName());
        v.setContentType(stored.contentType());
        v.setFileSize(stored.fileSize());
        v.setUploadedBy(uploadedBy);
        v.setChangeNotes(changeNotes);
        v.setCurrent(true);
        versionRepository.save(v);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        long maxBytes = maxUploadMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File exceeds the " + maxUploadMb + " MB upload limit.");
        }
    }

    private record StoredFile(String storedFileName, String originalFileName, String contentType, Long fileSize) {}

    private StoredFile storeOnDisk(MultipartFile file) {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) extension = originalName.substring(dot);

        try {
            Path root = uploadRoot();
            Files.createDirectories(root);
            String storedName = UUID.randomUUID() + extension;
            Path destination = root.resolve(storedName).normalize();
            if (!destination.startsWith(root)) {
                throw new IllegalArgumentException("Invalid file name.");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(storedName, originalName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            log.error("Failed to store File Center upload: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to store the uploaded file. Please try again.");
        }
    }

    private Path resolveOnDisk(String storedFileName) {
        Path root = uploadRoot();
        Path path = root.resolve(storedFileName).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new ResourceNotFoundException("File is missing on disk.");
        }
        return path;
    }

    private void deleteFromDisk(String storedFileName) {
        try {
            Path path = uploadRoot().resolve(storedFileName).normalize();
            if (path.startsWith(uploadRoot())) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete File Center file '{}': {}", storedFileName, e.getMessage());
        }
    }

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}
