package com.vikkash.assetmanagementv1.security;

import jakarta.annotation.PostConstruct;
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
 * Encrypts/decrypts recoverable secrets (network device passwords) using
 * AES-256-GCM.
 *
 * Deliberately NOT BCrypt/Argon2: those are one-way hashes meant for login
 * credentials that are only ever *verified*, never re-displayed. Network
 * device passwords must be recoverable so an admin can view/copy them to
 * actually log into the device — so they're encrypted (reversible), not
 * hashed (irreversible).
 *
 * GCM mode is used (not plain CBC) because it provides authenticated
 * encryption: any tampering with the stored ciphertext is detected on
 * decrypt (throws) rather than silently producing corrupted garbage.
 *
 * Output format: Base64( IV (12 bytes) || ciphertext+authTag ).
 * The IV is random per encryption call and safe to store alongside the
 * ciphertext — GCM's security does not depend on the IV being secret, only
 * on it never being reused with the same key.
 */
@Component
public class CredentialEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    @Value("${app.encryption.secret}")
    private String configuredSecret;
    @PostConstruct
    public void init() {
        System.out.println("====================================");
        System.out.println("Encryption Secret = " + configuredSecret);
        System.out.println("====================================");
    }
    private SecretKeySpec keySpec() {
        try {
            // Derive a fixed 256-bit key from the configured secret via SHA-256,
            // so the operator-supplied secret can be any length/passphrase while
            // the actual AES key is always a valid 256-bit key.
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /** Encrypts plaintext, returning a Base64 string safe to store in a TEXT column. Returns null for null input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /** Decrypts a string previously produced by encrypt(). Returns null for null input. */
    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt credential — the stored value may be corrupted or the encryption key has changed.", e);
        }
    }
}
