package com.vikkash.assetmanagementv1.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles physical storage of uploaded files on LOCAL DISK, at a fixed,
 * absolute, configurable location — never inside the application's
 * temporary directory, and never relative to whatever the JVM's current
 * working directory happens to be at launch time.
 *
 * The configured root ({@code app.upload.dir}) is resolved and created
 * exactly once at startup ({@link #init()}), so any misconfiguration
 * (e.g. an unwritable drive) fails fast with a clear error instead of
 * silently falling back to somewhere unexpected the first time someone
 * uploads a file.
 *
 * Only a randomly generated filename (UUID + original extension) is ever
 * returned to callers — never the original filename and never an absolute
 * path — so that:
 *   - Two different uploads never collide.
 *   - The original filename (which came from an untrusted client) can
 *     never be used to escape the upload root via path traversal
 *     (e.g. "../../../etc/passwd").
 *   - What's persisted in the database is a small, storage-location-agnostic
 *     token, not a machine-specific absolute path.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** Only PDF invoices are accepted — matches the business rule already enforced by ServiceBillingService. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");

    @Value("${app.upload.dir}")
    private String configuredUploadDir;

    @Value("${app.upload.max-file-size-mb:20}")
    private long maxFileSizeMb;

    private Path rootLocation;

    /**
     * Resolves the configured directory to an absolute, normalized path and
     * creates it (and any missing parent folders) immediately at startup.
     * Runs once per application boot — NOT per-upload — so a permissions or
     * disk problem surfaces the moment you start the app, not the first time
     * a user tries to upload an invoice.
     */
    @PostConstruct
    public void init() {
        if (configuredUploadDir == null || configuredUploadDir.isBlank()) {
            throw new IllegalStateException(
                    "app.upload.dir is not set. Configure an absolute local-disk path, e.g. " +
                    "app.upload.dir=C:/HaodaAsset/uploads/invoices");
        }

        // Fail-fast guard: a Windows-style drive path (e.g. "C:/...") is only
        // absolute ON WINDOWS. On Linux/macOS (e.g. a deployed container) it is
        // silently treated as RELATIVE and resolves under whatever the process's
        // working directory happens to be (e.g. "/app/C:/HaodaAsset/..."). That
        // failure mode is exactly what this whole rework was meant to eliminate,
        // so it's better to refuse to start than to silently create a bogus
        // nested folder that will look "empty" after every restart/redeploy.
        boolean looksLikeWindowsPath = configuredUploadDir.matches("^[A-Za-z]:[/\\\\].*");
        boolean runningOnWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (looksLikeWindowsPath && !runningOnWindows) {
            throw new IllegalStateException(
                    "app.upload.dir is set to a Windows-style path ('" + configuredUploadDir + "') but this " +
                    "process is running on a non-Windows OS (os.name=" + System.getProperty("os.name") + "). " +
                    "That path is NOT absolute on Linux/macOS and would silently resolve under the process's " +
                    "working directory instead. Set the INVOICE_UPLOAD_DIR environment variable to a Linux-style " +
                    "absolute path for this environment, e.g. INVOICE_UPLOAD_DIR=/var/data/uploads/invoices " +
                    "(and make sure that location is on a persistent, mounted volume if this is a container/cloud " +
                    "deployment — container filesystems are usually wiped on every restart/redeploy otherwise).");
        }

        this.rootLocation = Paths.get(configuredUploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create/access the invoice upload directory: " + rootLocation +
                    ". Check that the drive exists and the app has write permission to it.", e);
        }
        log.info("Invoice file storage ready at: {}", rootLocation);
    }

    public Path getRootLocation() {
        return rootLocation;
    }

    /**
     * Validates and stores an uploaded PDF under a brand-new UUID filename.
     * Returns only the stored FILENAME (e.g. "3f2a1c9e-....pdf") — this is
     * what callers should persist in the database, never an absolute path.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload.");
        }
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String storedName = UUID.randomUUID() + "." + extension;

        try {
            // Self-heals if the folder was deleted/moved after startup (e.g. an
            // external disk was unplugged and reattached) instead of failing forever.
            Files.createDirectories(rootLocation);

            Path destination = resolve(storedName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored uploaded file '{}' as '{}' at {}", originalName, storedName, destination);
            return storedName;
        } catch (IOException e) {
            log.error("Failed to store uploaded file '{}': {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded file to disk. Please try again.", e);
        }
    }

    /** True if a stored filename currently exists on disk under the configured root. */
    public boolean exists(String storedName) {
        if (storedName == null || storedName.isBlank()) return false;
        try {
            return Files.exists(resolve(storedName));
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Deletes a stored file if present. Never throws — a missing/undeletable old file must not block the caller. */
    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            log.warn("Could not delete stored file '{}': {}", storedName, e.getMessage());
        }
    }

    /**
     * Resolves a stored filename to its absolute path on disk, defending
     * against path traversal (a filename containing "../" could otherwise
     * escape the upload root). Callers only ever pass back filenames that
     * {@link #store} itself generated (random UUIDs), so this should never
     * legitimately fail — its purpose is to make traversal impossible even
     * if a corrupted/tampered value ever reaches here.
     */
    public Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }
        Path candidate = rootLocation.resolve(storedName).normalize();
        if (!candidate.getParent().equals(rootLocation)) {
            throw new SecurityException("Rejected invalid/unsafe file path: " + storedName);
        }
        return candidate;
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

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "invoice.pdf" : name.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1);
    }
}
