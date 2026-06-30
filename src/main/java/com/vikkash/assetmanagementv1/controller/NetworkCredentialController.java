package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.CredentialOtpVerifyRequest;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialCreateRequest;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialResponse;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialUpdateRequest;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.dto.RevealedCredentialResponse;
import com.vikkash.assetmanagementv1.dto.UnlockStatusResponse;
import com.vikkash.assetmanagementv1.service.CredentialUnlockService;
import com.vikkash.assetmanagementv1.service.NetworkCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Network Credentials module.
 * Requires ROLE_ADMIN (enforced in SecurityConfig for /api/network/**).
 *
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource() —
 * no per-controller @CrossOrigin here, consistent with the rest of the app.
 */
@RestController
@RequestMapping("/api/network")
public class NetworkCredentialController {

    private final NetworkCredentialService service;
    private final CredentialUnlockService unlockService;

    public NetworkCredentialController(NetworkCredentialService service, CredentialUnlockService unlockService) {
        this.service = service;
        this.unlockService = unlockService;
    }

    @GetMapping
    public List<NetworkCredentialResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.getDashboardStats();
    }

    @GetMapping("/search")
    public List<NetworkCredentialResponse> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/{id}")
    public NetworkCredentialResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<NetworkCredentialResponse> create(@Valid @RequestBody NetworkCredentialCreateRequest request,
                                                              Authentication authentication) {
        NetworkCredentialResponse created = service.create(request, authentication.getName());
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NetworkCredentialResponse> update(@PathVariable Long id,
                                                              @Valid @RequestBody NetworkCredentialUpdateRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(service.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Network credential deleted successfully"));
    }

    /**
     * Decrypts and returns the device login password. Requires an active
     * credential-unlock window (see /credential-access/*) — never callable
     * directly off a fresh login without a verified OTP.
     */
    @GetMapping("/{id}/reveal-password")
    public RevealedCredentialResponse revealPassword(@PathVariable Long id, Authentication authentication) {
        unlockService.assertUnlocked(authentication.getName());
        return new RevealedCredentialResponse(service.revealPassword(id, authentication.getName()));
    }

    @GetMapping("/{id}/reveal-enable-password")
    public RevealedCredentialResponse revealEnablePassword(@PathVariable Long id, Authentication authentication) {
        unlockService.assertUnlocked(authentication.getName());
        return new RevealedCredentialResponse(service.revealEnablePassword(id, authentication.getName()));
    }

    // ── Credential unlock (OTP gate for password / enable-password / copy actions) ──

    /** Generates a fresh OTP and emails it to the logged-in admin's registered address. */
    @PostMapping("/credential-access/request-otp")
    public ResponseEntity<OtpRequestResponse> requestUnlockOtp(Authentication authentication) {
        return ResponseEntity.ok(unlockService.requestOtp(authentication.getName()));
    }

    /** Verifies the OTP and, on success, opens the time-boxed unlock window. */
    @PostMapping("/credential-access/verify-otp")
    public ResponseEntity<UnlockStatusResponse> verifyUnlockOtp(@Valid @RequestBody CredentialOtpVerifyRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(unlockService.verifyOtp(authentication.getName(), request.getOtp()));
    }

    /** Lets the UI poll/restore unlock state (e.g. after a page refresh) without re-sending an OTP. */
    @GetMapping("/credential-access/status")
    public ResponseEntity<UnlockStatusResponse> unlockStatus(Authentication authentication) {
        return ResponseEntity.ok(unlockService.status(authentication.getName()));
    }

    /** Lets the UI explicitly re-lock (e.g. on tab close / manual "hide all") before the timer would. */
    @PostMapping("/credential-access/lock")
    public ResponseEntity<Map<String, String>> lock(Authentication authentication) {
        unlockService.lock(authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Credentials locked."));
    }
}
