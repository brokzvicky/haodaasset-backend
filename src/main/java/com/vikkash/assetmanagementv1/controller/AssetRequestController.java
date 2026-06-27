package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AssetRequestStatusDTO;
import com.vikkash.assetmanagementv1.entity.AssetRequest;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRequestRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-only asset request management.
 *
 * Routes secured at /api/admin/** → requires ROLE_ADMIN (SecurityConfig).
 *
 * Employee self-service routes (submit request, view own requests) live in
 * EmployeeSelfController under /api/employee/**, keeping concerns separate.
 * The duplicate employee-facing endpoints that previously existed here have
 * been removed to prevent routing conflicts and duplicated business logic.
 *
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource().
 */
@RestController
public class AssetRequestController {

    private final AssetRequestRepository assetRequestRepository;

    public AssetRequestController(AssetRequestRepository assetRequestRepository) {
        this.assetRequestRepository = assetRequestRepository;
    }

    /** GET /api/admin/requests — list all requests, newest first. */
    @GetMapping("/api/admin/requests")
    public List<AssetRequest> allRequests() {
        return assetRequestRepository.findAllByOrderByRequestedAtDesc();
    }

    /** PUT /api/admin/requests/{id}/status — approve or reject a request. */
    @PutMapping("/api/admin/requests/{id}/status")
    public ResponseEntity<AssetRequest> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody AssetRequestStatusDTO dto) {
        AssetRequest request = assetRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset request not found: " + id));

        request.setStatus(dto.getStatus());
        request.setResolvedAt(LocalDateTime.now());
        return ResponseEntity.ok(assetRequestRepository.save(request));
    }
}
