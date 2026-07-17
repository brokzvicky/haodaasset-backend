package com.vikkash.assetmanagementv1.security;

import jakarta.annotation.PostConstruct;
import com.vikkash.assetmanagementv1.exception.CredentialDecryptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts recoverable secrets (network device passwords)
 * using AES-256-GCM.
 */
@Component
public class CredentialEncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionUtil.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    @Value("${app.encryption.secret}")
    private String configuredSecret;

    @PostConstruct
    public void init() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.secret (CREDENTIAL_ENCRYPTION_SECRET) is not configured.");
        }
        // Log presence/length only — never the secret value itself.
        System.out.println("Credential encryption secret loaded (" + configuredSecret.length() + " chars).");
    }

    private SecretKeySpec keySpec() {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Encrypts plaintext.
     */
    public String encrypt(String plaintext) {

        if (plaintext == null) {
            return null;
        }

        try {

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keySpec(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] cipherText = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypts ciphertext produced by encrypt().
     */
    public String decrypt(String encoded) {

        if (encoded == null) {
            return null;
        }

        try {

            byte[] combined = Base64.getDecoder().decode(encoded);

            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                // Too short to even contain an IV + ciphertext/tag — definitely not
                // something this class ever produced. Fail fast with a clear signal
                // instead of letting ByteBuffer throw an opaque BufferUnderflowException.
                throw new IllegalArgumentException(
                        "Stored value (" + combined.length + " bytes decoded) is shorter than the "
                                + GCM_IV_LENGTH_BYTES + "-byte IV — not valid AES-GCM ciphertext.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keySpec(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // Log the root exception's CLASS (never the secret, never the plaintext/ciphertext)
            // so an operator can immediately tell key-mismatch vs malformed/corrupted data apart
            // from the server log alone, without needing to reproduce the request.
            //  - AEADBadTagException      -> ciphertext doesn't match the CURRENT encryption key
            //                                (secret was rotated, or this row came from a
            //                                different environment/secret).
            //  - IllegalArgumentException -> stored value isn't valid Base64 / valid GCM length
            //                                (legacy-corrupted row, or manually-inserted test data
            //                                that was never actually encrypted).
            //  - BufferUnderflowException -> same as above, different code path.
            log.error("Credential decryption failed — root cause: {}: {}",
                    e.getClass().getName(), e.getMessage());
            throw new CredentialDecryptionException(
                    "Failed to decrypt credential. The stored value may be corrupted, or it was "
                            + "encrypted with a different key than the one currently configured "
                            + "(CREDENTIAL_ENCRYPTION_SECRET). Re-enter and save this credential's "
                            + "password to fix it.",
                    e
            );
        }
    }
}