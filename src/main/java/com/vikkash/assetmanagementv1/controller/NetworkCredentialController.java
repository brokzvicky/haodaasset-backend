package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.NetworkCredentialCreateRequest;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialResponse;
import com.vikkash.assetmanagementv1.dto.NetworkCredentialUpdateRequest;
import com.vikkash.assetmanagementv1.dto.RevealedCredentialResponse;
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

    public NetworkCredentialController(NetworkCredentialService service) {
        this.service = service;
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
     * Decrypts and returns the device login password.
     * Only ever called when an admin explicitly clicks "Show Password" for
     * this specific row — the password never travels over the wire as part
     * of any other response.
     */
    @GetMapping("/{id}/reveal-password")
    public RevealedCredentialResponse revealPassword(@PathVariable Long id, Authentication authentication) {
        return new RevealedCredentialResponse(service.revealPassword(id, authentication.getName()));
    }

    @GetMapping("/{id}/reveal-enable-password")
    public RevealedCredentialResponse revealEnablePassword(@PathVariable Long id, Authentication authentication) {
        return new RevealedCredentialResponse(service.revealEnablePassword(id, authentication.getName()));
    }
}
