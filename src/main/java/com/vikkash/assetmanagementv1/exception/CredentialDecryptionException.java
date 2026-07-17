package com.vikkash.assetmanagementv1.exception;

/**
 * Thrown when stored ciphertext cannot be decrypted with the currently
 * configured {@code app.encryption.secret}. This is intentionally distinct
 * from a generic {@link IllegalStateException} so it can be classified,
 * logged, and surfaced to the client separately from unrelated "invalid
 * state" failures elsewhere in the app.
 *
 * Root causes are always one of:
 *  1. The AES key used at encrypt-time no longer matches the key configured
 *     now (CREDENTIAL_ENCRYPTION_SECRET was rotated, or this DB row/dump
 *     came from an environment with a different secret).
 *  2. The stored ciphertext itself is corrupted / not valid AES-GCM output
 *     (e.g. legacy rows written before the entity's @Lob mapping was fixed,
 *     or a row that was inserted manually rather than through encrypt()).
 *
 * Both are data-state problems, not something a retry fixes — the caller
 * must re-enter the password (via PUT /api/network/{id}) to re-encrypt it
 * under the current key/mapping.
 */
public class CredentialDecryptionException extends RuntimeException {

    public CredentialDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
