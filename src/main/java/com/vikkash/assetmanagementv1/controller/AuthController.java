package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.AdminLoginRequest;
import com.vikkash.assetmanagementv1.dto.ChangePasswordRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeLoginRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.service.AdminService;
import com.vikkash.assetmanagementv1.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication surface consumed by the React Login page.
 * Both endpoints are permitted without a token (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminService adminService;
    private final EmployeeService employeeService;

    public AuthController(AdminService adminService, EmployeeService employeeService) {
        this.adminService = adminService;
        this.employeeService = employeeService;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<LoginResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminService.login(request));
    }

    @PostMapping("/employee/login")
    public ResponseEntity<LoginResponse> employeeLogin(@Valid @RequestBody EmployeeLoginRequest request) {
        return ResponseEntity.ok(employeeService.login(request));
    }

    /**
     * Used right after a first-time login when mustChangePassword=true, and
     * also available any time from the "Change Password" screen.
     */
    @PostMapping("/employee/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        employeeService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }
}
