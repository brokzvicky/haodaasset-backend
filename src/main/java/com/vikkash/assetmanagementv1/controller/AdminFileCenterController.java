package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.*;
import com.vikkash.assetmanagementv1.entity.SharedFile;
import com.vikkash.assetmanagementv1.service.SharedFileService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Haoda File Center — admin API. Upload/edit/delete/replace files, resolve
 * and preview recipient selections, and pull analytics/download history.
 * Mapped under /api/admin/** so the ADMIN role guard applies automatically
 * (SecurityConfig) — no security changes needed for this module.
 */
@RestController
@RequestMapping("/api/admin/filecenter")
public class AdminFileCenterController {

    private final SharedFileService fileService;

    public AdminFileCenterController(SharedFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return fileService.getCategories();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("maxUploadMb", fileService.getMaxUploadMb());
    }

    // ── Recipient selection ─────────────────────────────────────────────────

    @PostMapping("/recipients/preview")
    public RecipientPreviewResponse previewRecipients(@Valid @RequestBody RecipientSelectionRequest req) {
        return fileService.previewRecipients(req.getRecipientType(), req.getRecipientValues());
    }

    // ── Files: list / detail / upload / edit / delete ───────────────────────

    @GetMapping("/files")
    public List<SharedFileSummaryDTO> list(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String priority,
                                            @RequestParam(required = false) String uploadedBy) {
        return fileService.listFiles(search, category, priority, uploadedBy);
    }

    @GetMapping("/files/{id}")
    public SharedFileDetailDTO detail(@PathVariable Long id) {
        return fileService.getDetail(id);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SharedFile> share(
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam String recipientType,
            @RequestParam(required = false) String recipientValues,
            @RequestParam(defaultValue = "true") boolean sendEmail,
            @RequestParam(required = false) String emailSubject,
            @RequestParam(required = false) String emailMessage,
            Authentication authentication) {

        String uploadedBy = authentication != null ? authentication.getName() : "unknown";
        SharedFile saved = fileService.shareFile(file, title, description, category, priority, tags, expiryDate,
                recipientType, splitCsv(recipientValues), sendEmail, emailSubject, emailMessage, uploadedBy);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/files/{id}")
    public SharedFile update(@PathVariable Long id, @RequestBody UpdateSharedFileRequest req, Authentication authentication) {
        String updatedBy = authentication != null ? authentication.getName() : "unknown";
        return fileService.updateMetadata(id, req, updatedBy);
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, Authentication authentication) {
        String deletedBy = authentication != null ? authentication.getName() : "unknown";
        fileService.deleteFile(id, deletedBy);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }

    // ── Replace with a newer version ────────────────────────────────────────

    @PostMapping(value = "/files/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SharedFile replaceVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String changeNotes,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String recipientValues,
            @RequestParam(defaultValue = "true") boolean sendEmail,
            @RequestParam(required = false) String emailSubject,
            @RequestParam(required = false) String emailMessage,
            Authentication authentication) {

        String uploadedBy = authentication != null ? authentication.getName() : "unknown";
        return fileService.replaceVersion(id, file, version, changeNotes, recipientType,
                splitCsv(recipientValues), sendEmail, emailSubject, emailMessage, uploadedBy);
    }

    // ── Admin download/preview of its own uploaded file ─────────────────────

    @GetMapping("/files/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        SharedFile meta = fileService.getEntity(id);
        Path path = fileService.resolveFileForAdmin(id);
        MediaType contentType = meta.getContentType() != null
                ? MediaType.parseMediaType(meta.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getOriginalFileName() + "\"")
                .body(new FileSystemResource(path));
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
