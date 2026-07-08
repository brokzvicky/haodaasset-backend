package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.ChangePasswordRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeCreateRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeLoginRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeUpdateRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.exception.DuplicateResourceException;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /** Organization-wide default password for all new employees. */
    public static final String DEFAULT_PASSWORD = "Haoda@321";

    private final EmployeeRepository employeeRepository;
    private final AssetRepository    assetRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtUtil            jwtUtil;

    public EmployeeService(EmployeeRepository employeeRepository,
                           AssetRepository assetRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.employeeRepository = employeeRepository;
        this.assetRepository    = assetRepository;
        this.passwordEncoder    = passwordEncoder;
        this.jwtUtil            = jwtUtil;
    }

    // ── Authentication ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoginResponse login(EmployeeLoginRequest request) {

        String empId = request.getEmployeeId().trim().toUpperCase();

        log.info("Login attempt for {}", empId);

        Employee employee = employeeRepository.findByEmployeeId(empId)
                .orElseThrow(() -> {
                    log.error("Employee NOT FOUND: {}", empId);
                    return new InvalidCredentialsException("Invalid Employee ID or password");
                });

        log.info("Employee found: {}", employee.getEmployeeId());

        boolean match = passwordEncoder.matches(request.getPassword(), employee.getPassword());

        log.info("Password match = {}", match);

        if (!match) {
            throw new InvalidCredentialsException("Invalid Employee ID or password");
        }

        String token = jwtUtil.generateToken(employee.getEmployeeId(), "EMPLOYEE");

        return LoginResponse.forEmployee(token, employee);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String empId = request.getEmployeeId().trim().toUpperCase();

        Employee employee = employeeRepository.findByEmployeeId(empId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + empId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), employee.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), employee.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        employee.setPassword(passwordEncoder.encode(request.getNewPassword()));
        employee.setMustChangePassword(false);
        employeeRepository.save(employee);
        log.info("Password changed for employee: {}", empId);
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    @Transactional
    public Employee createEmployee(EmployeeCreateRequest request) {
        String empId = request.getEmployeeId().trim().toUpperCase();

        if (employeeRepository.existsByEmployeeId(empId)) {
            throw new DuplicateResourceException("Employee ID already exists: " + empId);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }

        Employee employee = new Employee();
        employee.setEmployeeId(empId);
        employee.setEmployeeName(request.getEmployeeName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setLocation(request.getLocation());
        employee.setRole("EMPLOYEE");
        employee.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        employee.setMustChangePassword(true);  // force change on first login

        log.info("Created employee: {}", empId);
        return employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Employee getByEmployeeId(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
    }

    @Transactional
    public Employee updateEmployee(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(employee.getEmail())
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }
        employee.setEmployeeId(request.getEmployeeId().trim().toUpperCase());
        employee.setEmployeeName(request.getEmployeeName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setLocation(request.getLocation());

        return employeeRepository.save(employee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        log.warn("Deleting employee id={}", id);
        employeeRepository.deleteById(id);
    }

    /**
     * Admin-triggered password reset. Sets the password back to the org default
     * and forces the employee to change it on their next login.
     *
     * BUG FIX: previously set mustChangePassword=false — incorrect.
     * After a reset the employee MUST be forced to change their password.
     */
    @Transactional
    public void resetToDefaultPassword(String employeeId) {
        Employee employee = getByEmployeeId(employeeId);
        employee.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        employee.setMustChangePassword(true);   // ← was incorrectly false
        employeeRepository.save(employee);
        log.info("Password reset to default for employee: {}", employeeId);
    }

    /** Returns all assets currently assigned to this employee. */
    @Transactional(readOnly = true)
    public List<Asset> getAssetsForEmployee(String employeeId) {
        return assetRepository.findByEmployeeId(employeeId.trim().toUpperCase());
    }
}
