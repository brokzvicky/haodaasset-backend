package com.vikkash.assetmanagementv1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Handles physical storage of uploaded invoice PDFs in Amazon S3, replacing
 * the previous local-disk {@code FileStorageService}.
 *
 * Every object is stored under a random UUID key (never the original
 * filename) inside the {@code invoices/} prefix of the configured bucket, so:
 *   - Two different uploads never collide.
 *   - The original filename (which came from an untrusted client) is never
 *     used to build the storage key, so there's no path/key traversal risk.
 *   - What's persisted in the database is a small, storage-location-agnostic
 *     S3 object key, not a machine-specific absolute path — so invoices
 *     survive redeploys/restarts on platforms like Render whose local
 *     filesystem is ephemeral.
 *
 * The bucket is expected to remain PRIVATE (no public read access). Access is
 * always brokered through this service — either by streaming bytes straight
 * through the Spring Boot backend (used by the View/Download invoice
 * endpoints, so existing Spring Security rules keep applying) or, if ever
 * needed, via a short-lived presigned URL (see {@link #generateFileUrl}).
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    /** Only PDF invoices are accepted — matches the business rule already enforced by ServiceBillingService. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");

    private static final String INVOICE_PREFIX = "invoices/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.upload.max-file-size-mb:20}")
    private long maxFileSizeMb;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private long presignedUrlExpiryMinutes;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Validates and uploads a PDF to S3 under a brand-new UUID key. Returns
     * only the S3 OBJECT KEY (e.g. "invoices/3f2a1c9e-....pdf") — this is
     * what callers should persist in the database, never a full URL (URLs
     * tie you to one bucket/region naming scheme and are easy to invalidate
     * accidentally by renaming things; the key is portable and always lets
     * us regenerate a fresh URL/stream on demand).
     */
    public String uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload.");
        }
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String key = INVOICE_PREFIX + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded invoice '{}' to S3 as key '{}' in bucket '{}'", originalName, key, bucketName);
            return key;
        } catch (IOException e) {
            log.error("Failed to read uploaded file '{}' before sending to S3: {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to read the uploaded file. Please try again.", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to upload the invoice to cloud storage. Please try again.", e);
        }
    }

    /**
     * Opens a live stream directly from S3 for the given key. The caller
     * (the controller) is responsible for closing the stream once the
     * response has been written — Spring's resource/response writers do
     * this automatically.
     */
    public ResponseInputStream<GetObjectResponse> downloadFile(String key) {
        requireKey(key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            log.info("Streaming invoice from S3 key '{}'", key);
            return s3Client.getObject(getRequest);
        } catch (NoSuchKeyException e) {
            log.warn("Requested S3 key does not exist: {}", key);
            throw new IllegalStateException(
                    "The invoice file could not be found in cloud storage. It may have been deleted "
                            + "or never finished uploading. Please re-upload the invoice.", e);
        } catch (S3Exception e) {
            log.error("S3 download failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to retrieve the invoice from cloud storage.", e);
        }
    }

    /** Deletes an object from S3. Never throws — a missing/undeletable old object must not block the caller. */
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) return;
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted S3 object '{}' from bucket '{}'", key, bucketName);
        } catch (S3Exception e) {
            log.warn("Could not delete S3 object '{}': {}", key, errorMessageOf(e));
        }
    }

    /** True if the given key currently exists in the bucket. */
    public boolean exists(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("Could not check existence of S3 object '{}': {}", key, errorMessageOf(e));
            return false;
        }
    }

    /**
     * Generates a short-lived (default 15 minute, configurable via
     * {@code aws.s3.presigned-url-expiry-minutes}) presigned GET URL for the
     * given key. Not used by the View/Download invoice endpoints (which
     * stream through the backend instead, so Spring Security's existing
     * ADMIN-role guard keeps applying) — provided as a general-purpose
     * building block, e.g. for a future "share invoice link" feature or
     * external integrations, without ever making the bucket itself public.
     */
    public String generateFileUrl(String key) {
        requireKey(key);
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(getRequest)
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.info("Generated presigned URL for S3 key '{}' (expires in {} minutes)", key, presignedUrlExpiryMinutes);
        return url;
    }

    private void validate(MultipartFile file) {
        long maxBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "File is too large (" + (file.getSize() / (1024 * 1024)) + "MB). Maximum allowed is "
                            + maxFileSizeMb + "MB.");
        }

        String originalName = originalNameOf(file);
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        String extension = extensionOf(originalName).toLowerCase();
        String contentType = file.getContentType();
        boolean extensionOk = ALLOWED_EXTENSIONS.contains(extension);
        boolean contentTypeOk = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());

        if (!extensionOk || !contentTypeOk) {
            throw new IllegalArgumentException("Only PDF files are allowed for invoices.");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("File key is required.");
        }
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "invoice.pdf" : name.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1);
    }

    private String errorMessageOf(S3Exception e) {
        return e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    }
}
