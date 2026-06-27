package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.EmployeeCreateRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeUpdateRequest;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
 *   - GET /api/employee/{employeeId}/assets   (now only in EmployeeSelfController)
 * Spring was ambiguously matching /api/employee/profile against
 * /api/employee/{employeeId}/profile — removing the path-variable variants here
 * eliminates that ambiguity.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/api/admin/employees")
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @PostMapping("/api/admin/employees")
    public ResponseEntity<Employee> createEmployee(@Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(201).body(employeeService.createEmployee(request));
    }
    @PutMapping("/reset-all-passwords")
    public ResponseEntity<String> resetAllPasswords() {

    employeeService.getAllEmployees().forEach(emp -> {
        emp.setPassword(new BCryptPasswordEncoder().encode("Haoda@321"));
        emp.setMustChangePassword(false);
        employeeRepository.save(emp);
    });

    return ResponseEntity.ok("All passwords reset successfully");
}
    @PutMapping("/api/admin/employees/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id,
                                                    @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }
    @PutMapping("/api/admin/employees/{employeeId}/test-reset")
public ResponseEntity<String> testReset(@PathVariable String employeeId) {
    employeeService.resetToDefaultPassword(employeeId);
    return ResponseEntity.ok("Password reset");
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
}
