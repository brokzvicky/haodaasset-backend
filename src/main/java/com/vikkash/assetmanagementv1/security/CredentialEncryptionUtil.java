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
 * Encrypts/decrypts recoverable secrets (network device passwords)
 * using AES-256-GCM.
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
            throw new IllegalStateException(
                    "Failed to decrypt credential. The stored value may be corrupted or the encryption key has changed.",
                    e
            );
        }
    }
}