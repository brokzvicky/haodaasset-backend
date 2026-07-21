package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.EmployeeCreateRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeSearchResponse;
import com.vikkash.assetmanagementv1.dto.EmployeeSeparationDetailDTO;
import com.vikkash.assetmanagementv1.dto.EmployeeUpdateRequest;
import com.vikkash.assetmanagementv1.dto.InitiateSeparationRequest;
import com.vikkash.assetmanagementv1.dto.SeparationRemarksRequest;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.EmploymentStatus;
import com.vikkash.assetmanagementv1.service.EmployeeAssetEmailService;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only employee directory management.
 *
 * All routes are under /api/admin/** which requires ROLE_ADMIN (SecurityConfig).
 *
 * Employee self-service routes (profile, assets, requests) are in
 * EmployeeSelfController under /api/employee/**, which enforces that each
 * employee can only read their own data using the JWT subject.
 *
 * REMOVED from this controller to eliminate routing conflicts:
 *   - GET /api/employee/{employeeId}/profile  (now only in EmployeeSelfController)
 *   - GET /api/employee/{employeeId}/assets   (now only in EmployeeSelfController, self-only)
 * Spring was ambiguously matching /api/employee/profile against
 * /api/employee/{employeeId}/profile — removing the path-variable variants here
 * eliminates that ambiguity.
 *
 * GET /api/admin/employees/{employeeId}/assets below is intentionally a
 * *different* path prefix (/api/admin/** rather than /api/employee/**), so
 * it does not reintroduce that ambiguity. It exists because admins need to
 * look up *any* employee's assigned assets (e.g. the directory's expand-row
 * view), whereas EmployeeSelfController's /api/employee/assets always
 * resolves to the caller's own JWT identity by design and has no path
 * variable to target another employee.
 *
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource().
 */
@RestController
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeAssetEmailService employeeAssetEmailService;

    public EmployeeController(EmployeeService employeeService, EmployeeAssetEmailService employeeAssetEmailService) {
        this.employeeService = employeeService;
        this.employeeAssetEmailService = employeeAssetEmailService;
    }

    @GetMapping("/api/admin/employees")
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    /**
     * Used by the "Send Asset Email" page's search box. Matches on
     * Employee ID, Employee Name, or Email. Returns an empty list for a
     * blank/missing query rather than the whole directory.
     */
    @GetMapping("/api/admin/employees/search")
    public List<EmployeeSearchResponse> searchEmployees(@RequestParam(name = "q", required = false, defaultValue = "") String q) {
        return employeeAssetEmailService.searchEmployees(q);
    }

    @PostMapping("/api/admin/employees")
    public ResponseEntity<Employee> createEmployee(@Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(201).body(employeeService.createEmployee(request));
    }

    @PutMapping("/api/admin/employees/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id,
                                                    @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }

    @DeleteMapping("/api/admin/employees/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok("Employee deleted successfully");
    }

    @PutMapping("/api/admin/employees/{employeeId}/reset-password")
    public ResponseEntity<String> resetPassword(@PathVariable String employeeId) {
        employeeService.resetToDefaultPassword(employeeId);
        return ResponseEntity.ok("Password reset to organization default. Employee must change it on next login.");
    }

    /**
     * GET /api/admin/employees/{employeeId}/assets
     * Admin-only lookup of any employee's assigned assets by employeeId
     * (e.g. EMP001), used by the directory's expand-row view.
     */
    @GetMapping("/api/admin/employees/{employeeId}/assets")
    public List<Asset> getEmployeeAssets(@PathVariable String employeeId) {
        return employeeService.getAssetsForEmployee(employeeId);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EMPLOYEE SEPARATION / RESIGNATION MANAGEMENT
    // ═════════════════════════════════════════════════════════════════════

    /** Reference vocabulary for the Resignation Reason dropdown. */
    @GetMapping("/api/admin/employees/separation/reasons")
    public List<String> getResignationReasons() {
        return EmploymentStatus.RESIGNATION_REASONS;
    }

    /** Dashboard widgets: Notice Period / Pending Exit Clearance / Resigned This Month / Pending Asset Returns. */
    @GetMapping("/api/admin/employees/separation/dashboard-stats")
    public Map<String, Long> getSeparationDashboardStats() {
        return employeeService.getSeparationDashboardStats();
    }

    /** Every employee currently in the separation pipeline or already Resigned — backs the Employee Exit Report. */
    @GetMapping("/api/admin/employees/separation/all")
    public List<Employee> getAllInSeparation() {
        return employeeService.getAllInSeparation();
    }

    /** Full separation detail (profile + workflow state + assigned/returned assets) for the Separation Modal. */
    @GetMapping("/api/admin/employees/{employeeId}/separation")
    public EmployeeSeparationDetailDTO getSeparationDetail(@PathVariable String employeeId) {
        return employeeService.getSeparationDetail(employeeId);
    }

    /** Active → Notice Period. Starts the resignation workflow and notifies HR. */
    @PostMapping("/api/admin/employees/{employeeId}/separation/initiate")
    public ResponseEntity<EmployeeSeparationDetailDTO> initiateSeparation(
            @PathVariable String employeeId, @Valid @RequestBody InitiateSeparationRequest request) {
        return ResponseEntity.ok(employeeService.initiateSeparation(employeeId, request));
    }

    /** Notice Period → Exit Clearance. Notifies IT that assets must be collected. */
    @PostMapping("/api/admin/employees/{employeeId}/separation/exit-clearance")
    public ResponseEntity<EmployeeSeparationDetailDTO> moveToExitClearance(
            @PathVariable String employeeId, @RequestBody(required = false) SeparationRemarksRequest request) {
        return ResponseEntity.ok(employeeService.moveToExitClearance(employeeId, request));
    }

    /**
     * Finalizes the resignation (Assets Returned → Resigned). Blocked with 409
     * + a list of pending assets if anything is still assigned.
     */
    @PostMapping("/api/admin/employees/{employeeId}/separation/complete")
    public ResponseEntity<EmployeeSeparationDetailDTO> completeResignation(
            @PathVariable String employeeId, @RequestBody(required = false) SeparationRemarksRequest request) {
        return ResponseEntity.ok(employeeService.completeResignation(employeeId, request));
    }

    /** Cancels an in-progress separation and restores the employee to Active. */
    @PostMapping("/api/admin/employees/{employeeId}/separation/cancel")
    public ResponseEntity<EmployeeSeparationDetailDTO> cancelSeparation(
            @PathVariable String employeeId, @RequestBody(required = false) SeparationRemarksRequest request) {
        return ResponseEntity.ok(employeeService.cancelSeparation(employeeId, request));
    }
}
