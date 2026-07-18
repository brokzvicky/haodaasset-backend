package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.MyFileDTO;
import com.vikkash.assetmanagementv1.entity.SharedFile;
import com.vikkash.assetmanagementv1.service.SharedFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Haoda File Center — "My Files" employee self-service API. Every route
 * derives the employee from the verified JWT subject (never trusts a
 * client-supplied ID), mirroring {@link EmployeeSelfController}. Mapped
 * under /api/employee/** so the EMPLOYEE/ADMIN role guard applies
 * automatically (SecurityConfig).
 */
@RestController
@RequestMapping("/api/employee/filecenter")
public class EmployeeFileCenterController {

    private final SharedFileService fileService;

    public EmployeeFileCenterController(SharedFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/my-files")
    public List<MyFileDTO> myFiles(Authentication auth) {
        return fileService.myFiles(auth.getName());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("unread", fileService.unreadCount(auth.getName()));
    }

    @PutMapping("/files/{id}/read")
    public ResponseEntity<Map<String, String>> markRead(@PathVariable Long id, Authentication auth) {
        fileService.markRead(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @GetMapping("/files/{id}/view")
    public ResponseEntity<FileSystemResource> view(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        SharedFile meta = fileService.getEntityForEmployeeDownload(id, auth.getName());
        Path path = fileService.recordAccess(id, auth.getName(), "VIEW", clientIp(request));
        MediaType contentType = meta.getContentType() != null
                ? MediaType.parseMediaType(meta.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getOriginalFileName() + "\"")
                .body(new FileSystemResource(path));
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        SharedFile meta = fileService.getEntityForEmployeeDownload(id, auth.getName());
        Path path = fileService.recordAccess(id, auth.getName(), "DOWNLOAD", clientIp(request));
        MediaType contentType = meta.getContentType() != null
                ? MediaType.parseMediaType(meta.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getOriginalFileName() + "\"")
                .body(new FileSystemResource(path));
    }

    /** Reads the real client IP even behind Render's reverse proxy, for the Download History log. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
