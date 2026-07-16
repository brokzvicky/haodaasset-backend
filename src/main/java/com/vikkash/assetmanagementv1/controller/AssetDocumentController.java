package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.entity.AssetDocument;
import com.vikkash.assetmanagementv1.service.AssetDocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Document Management: upload/list/download/delete supporting files
 * (invoices, warranty cards, manuals, insurance papers) attached to an
 * asset. Mapped under /api/admin/** so the ADMIN role guard applies
 * automatically (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/assets/{assetId}/documents")
public class AssetDocumentController {

    private final AssetDocumentService documentService;

    public AssetDocumentController(AssetDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<AssetDocument> list(@PathVariable Long assetId) {
        return documentService.listForAsset(assetId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetDocument> upload(@PathVariable Long assetId,
                                                 @RequestParam(value = "documentType", required = false) String documentType,
                                                 @RequestParam("file") MultipartFile file,
                                                 Authentication authentication) {
        String uploadedBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.status(201).body(documentService.upload(assetId, documentType, file, uploadedBy));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long assetId, @PathVariable Long documentId) {
        AssetDocument meta = documentService.getMetadata(documentId);
        Path file = documentService.resolveFile(documentId);
        MediaType contentType = meta.getContentType() != null
                ? MediaType.parseMediaType(meta.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getOriginalFileName() + "\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/{documentId}/view")
    public ResponseEntity<FileSystemResource> view(@PathVariable Long assetId, @PathVariable Long documentId) {
        AssetDocument meta = documentService.getMetadata(documentId);
        Path file = documentService.resolveFile(documentId);
        MediaType contentType = meta.getContentType() != null
                ? MediaType.parseMediaType(meta.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getOriginalFileName() + "\"")
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long assetId, @PathVariable Long documentId,
                                                        Authentication authentication) {
        String deletedBy = authentication != null ? authentication.getName() : "unknown";
        documentService.delete(documentId, deletedBy);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }
}
