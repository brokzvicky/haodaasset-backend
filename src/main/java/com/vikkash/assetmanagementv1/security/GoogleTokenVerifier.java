package com.vikkash.assetmanagementv1.security;

import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies a Google Sign-In ID token from the frontend (Google Identity
 * Services JS library — {@code google.accounts.id}) using Google's public
 * tokeninfo endpoint, rather than pulling in a full Google API client SDK
 * dependency. This is the standard lightweight verification path for an SPA
 * that already obtained the ID token client-side; it still fully validates
 * the token's signature/expiry (Google does that server-side when asked to
 * decode it) and additionally checks the audience and email_verified claims
 * ourselves.
 *
 * NOTE: requires {@code app.google.client-id} to be configured (the OAuth
 * 2.0 Web Client ID from Google Cloud Console) — without it, every Google
 * login attempt is rejected rather than silently accepting tokens meant for
 * a different application.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final RestTemplate restTemplate;

    @Value("${app.google.client-id:}")
    private String expectedClientId;

    public GoogleTokenVerifier(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static final class VerifiedGoogleIdentity {
        public final String googleId;   // "sub" claim — stable, unique per Google account
        public final String email;
        public final boolean emailVerified;
        public final String name;
        public final String pictureUrl;

        VerifiedGoogleIdentity(String googleId, String email, boolean emailVerified, String name, String pictureUrl) {
            this.googleId = googleId;
            this.email = email;
            this.emailVerified = emailVerified;
            this.name = name;
            this.pictureUrl = pictureUrl;
        }
    }

    @SuppressWarnings("unchecked")
    public VerifiedGoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidCredentialsException("Google ID token is required.");
        }
        if (expectedClientId == null || expectedClientId.isBlank()) {
            log.error("Google Sign-In attempted but app.google.client-id is not configured.");
            throw new InvalidCredentialsException("Google Sign-In is not configured on this server.");
        }

        Map<String, Object> claims;
        try {
            claims = restTemplate.getForObject(TOKENINFO_URL + idToken, Map.class);
        } catch (RestClientException ex) {
            log.debug("Google tokeninfo verification failed: {}", ex.getMessage());
            throw new InvalidCredentialsException("Could not verify Google sign-in. Please try again.");
        }

        if (claims == null) {
            throw new InvalidCredentialsException("Could not verify Google sign-in. Please try again.");
        }

        String aud = String.valueOf(claims.get("aud"));
        if (!expectedClientId.equals(aud)) {
            log.warn("Google ID token audience mismatch (token was not issued for this application).");
            throw new InvalidCredentialsException("This Google sign-in token was not issued for this application.");
        }

        boolean emailVerified = "true".equals(String.valueOf(claims.get("email_verified")));
        String email = (String) claims.get("email");
        if (email == null || !emailVerified) {
            throw new InvalidCredentialsException("Your Google account's email is not verified.");
        }

        String googleId = (String) claims.get("sub");
        String name = (String) claims.getOrDefault("name", email);
        String picture = (String) claims.get("picture");

        return new VerifiedGoogleIdentity(googleId, email.toLowerCase(), true, name, picture);
    }
}
