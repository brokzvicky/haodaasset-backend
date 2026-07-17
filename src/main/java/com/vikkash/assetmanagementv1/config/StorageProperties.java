package com.vikkash.assetmanagementv1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves a configured storage directory into a single, stable absolute
 * path, exactly once, and fails fast if it isn't usable.
 *
 * IMPORTANT: in production this MUST point at a persistent volume/disk
 * (e.g. a mounted disk on Render/EC2/EFS), not the container's local,
 * ephemeral filesystem. A relative path (e.g. "uploads/invoices") is
 * NEVER safe here — its absolute resolution depends on the JVM's working
 * directory at process start, which is not guaranteed to be identical
 * across restarts, redeploys, or different launch methods. Always
 * configure this via an absolute path env var in production.
 */
public final class StorageProperties {

    private static final Logger log = LoggerFactory.getLogger(StorageProperties.class);

    private final Path root;

    public StorageProperties(String configuredDir, String label) {
        if (configuredDir == null || configuredDir.isBlank()) {
            throw new IllegalStateException(label + " storage directory is not configured.");
        }

        Path resolved = Paths.get(configuredDir).toAbsolutePath().normalize();

        if (!resolved.isAbsolute()) {
            // Should be unreachable after toAbsolutePath(), kept as a hard guard.
            throw new IllegalStateException(label + " storage path did not resolve to an absolute path: " + resolved);
        }

        try {
            Files.createDirectories(resolved);
            // Fail fast if we can't actually write here (permissions, read-only fs, etc).
            Path probe = resolved.resolve(".write-test-" + System.nanoTime());
            Files.writeString(probe, "ok");
            Files.delete(probe);
        } catch (IOException e) {
            throw new IllegalStateException(
                    label + " storage directory is not writable: " + resolved
                            + ". Check disk mounts/permissions before starting the app.", e);
        }

        this.root = resolved;

        // Loud, unmissable log line so a working-directory / mount change is
        // visible immediately in the logs, instead of surfacing later as a
        // silent 404 on download.
        log.info("[{} STORAGE] Resolved root directory: {}", label, this.root);
    }

    public Path root() {
        return root;
    }

    /** Resolves a stored relative filename against the root, guarding against path traversal. */
    public Path resolve(String relativeName) {
        Path path = root.resolve(relativeName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid stored file path: " + relativeName);
        }
        return path;
    }
}
