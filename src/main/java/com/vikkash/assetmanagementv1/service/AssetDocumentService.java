package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.config.StorageProperties;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.AssetDocument;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetDocumentRepository;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Document Management: lets admins attach supporting files (invoices,
 * warranty cards, manuals, insurance papers) to any asset. Files are stored
 * on disk under the resolved {@link StorageProperties} root, named with a
 * random UUID; only metadata + the stored (safe) file name is persisted in
 * the database — same pattern used for Service Billing invoices, and now
 * sharing the same hardened storage-resolution logic.
 */
@Service
public class AssetDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AssetDocumentService.class);

    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024; // 15 MB

    private final AssetDocumentRepository documentRepository;
    private final AssetRepository assetRepository;
    private final AuditLogService auditLogService;

    @Value("${app.storage.document-upload-dir:uploads/asset-documents}")
    private String uploadDir;

    private StorageProperties storage;

    public AssetDocumentService(AssetDocumentRepository documentRepository,
                                 AssetRepository assetRepository,
                                 AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.assetRepository = assetRepository;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    void init() {
        this.storage = new StorageProperties(uploadDir, "DOCUMENT");
    }

    @Transactional(readOnly = true)
    public List<AssetDocument> listForAsset(Long assetId) {
        return documentRepository.findByAssetIdOrderByUploadedAtDesc(assetId);
    }

    @Transactional
    public AssetDocument upload(Long assetId, String documentType, MultipartFile file, String uploadedBy) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 15 MB upload limit.");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) extension = originalName.substring(dot);

        try {
            String storedName = UUID.randomUUID() + extension;
            Path destination = storage.resolve(storedName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            AssetDocument doc = new AssetDocument();
            doc.setAssetId(assetId);
            doc.setDocumentType((documentType == null || documentType.isBlank()) ? "Other" : documentType);
            doc.setStoredFileName(storedName);
            doc.setOriginalFileName(originalName);
            doc.setContentType(file.getContentType());
            doc.setFileSize(file.getSize());
            doc.setUploadedBy(uploadedBy);

            AssetDocument saved = documentRepository.save(doc);

            auditLogService.record("ASSET", String.valueOf(assetId), "DOCUMENT_UPLOADED",
                    "Uploaded document '" + originalName + "' (" + doc.getDocumentType() + ") for asset '"
                            + asset.getLaptopName() + "'", uploadedBy);

            log.info("Stored asset document id={} at {}", saved.getId(), destination);
            return saved;
        } catch (IOException e) {
            log.error("Failed to store asset document: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to store the uploaded document. Please try again.");
        }
    }

    @Transactional(readOnly = true)
    public AssetDocument getMetadata(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
    }

    @Transactional(readOnly = true)
    public Path resolveFile(Long documentId) {
        AssetDocument doc = getMetadata(documentId);
        Path path = storage.resolve(doc.getStoredFileName());
        if (!Files.exists(path)) {
            log.error("Document DB record exists (documentId={}, storedFileName={}) but file is missing at " +
                            "resolved location {}. Storage root in use: {}. Check the storage volume/disk mount.",
                    documentId, doc.getStoredFileName(), path, storage.root());
            throw new ResourceNotFoundException("Document file is missing on disk.");
        }
        return path;
    }

    @Transactional
    public void delete(Long documentId, String deletedBy) {
        AssetDocument doc = getMetadata(documentId);
        try {
            Path path = storage.resolve(doc.getStoredFileName());
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete document file '{}': {}", doc.getStoredFileName(), e.getMessage());
        }
        documentRepository.delete(doc);
        auditLogService.record("ASSET", String.valueOf(doc.getAssetId()), "DOCUMENT_DELETED",
                "Deleted document '" + doc.getOriginalFileName() + "'", deletedBy);
    }
}
