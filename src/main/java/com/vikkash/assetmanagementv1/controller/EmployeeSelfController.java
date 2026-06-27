package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AssetRequestCreateDTO;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.AssetRequest;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRequestRepository;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All routes under /api/employee/** that an employee calls for their own data.
 *
 * Security contract:
 *  - ROLE_EMPLOYEE  → can only access routes where the JWT subject matches the requested employeeId
 *  - ROLE_ADMIN     → may access any employee's data (admin helpdesk use case)
 *
 * The JWT subject is the employeeId (set in AuthController at login time).
 * We never trust the client to send their own employeeId; we always read it
 * from the verified JWT subject stored in the SecurityContext.
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource().
 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeSelfController {

    private final EmployeeService employeeService;
    private final AssetRequestRepository assetRequestRepository;

    public EmployeeSelfController(EmployeeService employeeService,
                                   AssetRequestRepository assetRequestRepository) {
        this.employeeService = employeeService;
        this.assetRequestRepository = assetRequestRepository;
    }

    // ─── Internal helpers ──────────────────────────────────────────────────────

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * Returns the employeeId the caller is allowed to act on.
     * For EMPLOYEE tokens: always returns the JWT subject (their own ID).
     * For ADMIN tokens: returns the requested ID (admin can view any employee).
     */
    private String resolveId(Authentication auth, String requested) {
        if (isAdmin(auth)) return requested;
        String jwtSubject = auth.getName();
        if (!jwtSubject.equalsIgnoreCase(requested)) {
            throw new AccessDeniedException("You may only access your own data.");
        }
        return jwtSubject;
    }

    /**
     * Convenience: returns the caller's own employee ID directly from the JWT.
     * Used for routes where the client doesn't send an ID in the URL at all —
     * the backend derives everything from the token.
     */
    private String ownId(Authentication auth) {
        return auth.getName();
    }

    // ─── Dashboard ─────────────────────────────────────────────────────────────

    /**
     * GET /api/employee/dashboard
     * Returns counts for the logged-in employee's dashboard cards.
     * No path variable needed — the ID comes from the JWT.
     */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        String id = ownId(auth);
        Employee employee = employeeService.getByEmployeeId(id);
        List<Asset> assets = employeeService.getAssetsForEmployee(id);
        List<AssetRequest> requests = assetRequestRepository
                .findByEmployeeIdOrderByRequestedAtDesc(id.toUpperCase());

        long pendingRequests = requests.stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", employee.getEmployeeId());
        data.put("name", employee.getEmployeeName());
        data.put("department", employee.getDepartment());
        data.put("designation", employee.getDesignation());
        data.put("location", employee.getLocation());
        data.put("assignedAssets", assets.size());
        data.put("pendingRequests", pendingRequests);
        data.put("profileStatus", "Active");
        return data;
    }

    // ─── Profile ───────────────────────────────────────────────────────────────

    /**
     * GET /api/employee/profile
     * Returns the full profile of the logged-in employee.
     */
    @GetMapping("/profile")
    public Employee profile(Authentication auth) {
        return employeeService.getByEmployeeId(ownId(auth));
    }

    // ─── Assets ────────────────────────────────────────────────────────────────

    /**
     * GET /api/employee/assets
     * Returns ONLY the assets assigned to the logged-in employee.
     */
    @GetMapping("/assets")
    public List<Asset> assets(Authentication auth) {
        return employeeService.getAssetsForEmployee(ownId(auth));
    }

    // ─── Asset Requests ────────────────────────────────────────────────────────

    /**
     * GET /api/employee/requests
     * Returns the request history of the logged-in employee.
     */
    @GetMapping("/requests")
    public List<AssetRequest> myRequests(Authentication auth) {
        String id = ownId(auth).toUpperCase();
        return assetRequestRepository.findByEmployeeIdOrderByRequestedAtDesc(id);
    }

    /**
     * POST /api/employee/request
     * Submits a new asset request for the logged-in employee.
     */
    @PostMapping("/request")
    public ResponseEntity<AssetRequest> submitRequest(@Valid @RequestBody AssetRequestCreateDTO dto,
                                                       Authentication auth) {
        String id = ownId(auth);
        Employee employee = employeeService.getByEmployeeId(id);

        AssetRequest req = new AssetRequest();
        req.setEmployeeId(employee.getEmployeeId());
        req.setEmployeeName(employee.getEmployeeName());
        req.setAssetType(dto.getAssetType());
        req.setUrgency(dto.getUrgency() != null && !dto.getUrgency().isBlank() ? dto.getUrgency() : "Normal");
        req.setReason(dto.getReason());
        req.setStatus("PENDING");
        req.setRequestedAt(LocalDateTime.now());

        return ResponseEntity.status(201).body(assetRequestRepository.save(req));
    }
}
