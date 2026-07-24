package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.ChangePasswordRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeCreateRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeLoginRequest;
import com.vikkash.assetmanagementv1.dto.EmployeeSeparationDetailDTO;
import com.vikkash.assetmanagementv1.dto.EmployeeUpdateRequest;
import com.vikkash.assetmanagementv1.dto.InitiateSeparationRequest;
import com.vikkash.assetmanagementv1.dto.LoginResponse;
import com.vikkash.assetmanagementv1.dto.MeResponse;
import com.vikkash.assetmanagementv1.dto.OtpRequestResponse;
import com.vikkash.assetmanagementv1.dto.ResignedEmployeeViewDTO;
import com.vikkash.assetmanagementv1.dto.SeparationRemarksRequest;
import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.AuthProvider;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.entity.EmploymentStatus;
import com.vikkash.assetmanagementv1.entity.Permission;
import com.vikkash.assetmanagementv1.entity.Role;
import com.vikkash.assetmanagementv1.exception.DuplicateResourceException;
import com.vikkash.assetmanagementv1.exception.InvalidCredentialsException;
import com.vikkash.assetmanagementv1.exception.PendingAssetReturnException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import com.vikkash.assetmanagementv1.security.GoogleTokenVerifier;
import com.vikkash.assetmanagementv1.security.JwtUtil;
import com.vikkash.assetmanagementv1.security.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /** Organization-wide default password for all new employees. */
    public static final String DEFAULT_PASSWORD = "Haoda@321";

    private final EmployeeRepository employeeRepository;
    private final AssetRepository    assetRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtUtil            jwtUtil;
    private final AuditLogService    auditLogService;
    private final SeparationNotificationService separationNotificationService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final OtpService         otpService;
    private final SmsService         smsService;

    /** Separate namespace for the "Login with Mobile" OTP flow. */
    private static final String MOBILE_LOGIN_NAMESPACE = "empmobilelogin:";

    public EmployeeService(EmployeeRepository employeeRepository,
                           AssetRepository assetRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           AuditLogService auditLogService,
                           SeparationNotificationService separationNotificationService,
                           GoogleTokenVerifier googleTokenVerifier,
                           OtpService otpService,
                           SmsService smsService) {
        this.employeeRepository = employeeRepository;
        this.assetRepository    = assetRepository;
        this.passwordEncoder    = passwordEncoder;
        this.jwtUtil            = jwtUtil;
        this.auditLogService    = auditLogService;
        this.separationNotificationService = separationNotificationService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.otpService         = otpService;
        this.smsService         = smsService;
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

        assertLoginAllowed(employee);
        return issueLoginResponse(employee);
    }

    /** Blocks login for any employee whose access has been disabled (Resigned / Terminated). */
    private void assertLoginAllowed(Employee employee) {
        if (!employee.isLoginEnabled()) {
            log.warn("Login blocked for {} — account disabled (status: {})", employee.getEmployeeId(), employee.getEmploymentStatus());
            throw new InvalidCredentialsException(
                    "This account has been disabled. Please contact HR/IT if you believe this is a mistake.");
        }
    }

    private LoginResponse issueLoginResponse(Employee employee) {
        String token = jwtUtil.generateToken(employee.getEmployeeId(), "EMPLOYEE", permissionCodes(employee.getRoleRef()));
        return LoginResponse.forEmployee(token, employee);
    }

    private List<String> permissionCodes(Role role) {
        if (role == null) return List.of();
        return role.getPermissions().stream().map(Permission::getCode).toList();
    }

    // ── Google Sign-In ───────────────────────────────────────────────────
    // Same "must already exist, never auto-provision" policy as AdminService
    // — an employee record is created by HR/Admin, not by whoever happens to
    // sign in with a matching Google account.
    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifier.VerifiedGoogleIdentity identity = googleTokenVerifier.verify(idToken);

        Employee employee = employeeRepository.findByGoogleId(identity.googleId)
                .or(() -> employeeRepository.findByEmail(identity.email))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "No employee account is registered for this Google account. Contact IT/HR to have it added."));

        if (employee.getGoogleId() == null) {
            employee.setGoogleId(identity.googleId);
            if (employee.getAuthProvider() == null) employee.setAuthProvider(AuthProvider.LOCAL);
            employeeRepository.save(employee);
            log.info("Linked Google identity to existing employee id={}", employee.getEmployeeId());
        }

        assertLoginAllowed(employee);
        log.info("Google Sign-In completed for employee id={}", employee.getEmployeeId());
        return issueLoginResponse(employee);
    }

    // ── Mobile OTP login ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public OtpRequestResponse requestMobileOtp(String mobile) {
        Employee employee = employeeRepository.findByMobile(mobile.trim())
                .orElseThrow(() -> new InvalidCredentialsException("No employee account is registered with this mobile number."));

        String key = MOBILE_LOGIN_NAMESPACE + employee.getMobile();
        String otp = otpService.generate(key);
        smsService.sendOtp(employee.getMobile(), otp, otpService.expiryMinutes());
        log.info("Mobile login OTP sent for employee id={}", employee.getEmployeeId());
        return new OtpRequestResponse(
                "A verification code has been sent to your registered mobile number.",
                otpService.expiryMinutes() * 60,
                otpService.secondsUntilResendAllowed(key));
    }

    @Transactional
    public LoginResponse verifyMobileOtp(String mobile, String otp) {
        Employee employee = employeeRepository.findByMobile(mobile.trim())
                .orElseThrow(() -> new InvalidCredentialsException("No employee account is registered with this mobile number."));

        otpService.verify(MOBILE_LOGIN_NAMESPACE + employee.getMobile(), otp);
        assertLoginAllowed(employee);
        log.info("Mobile OTP login completed for employee id={}", employee.getEmployeeId());
        return issueLoginResponse(employee);
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MeResponse buildMeResponse(String employeeId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        MeResponse me = new MeResponse();
        me.setRole("EMPLOYEE");
        Role role = employee.getRoleRef();
        if (role != null) {
            me.setRoleName(role.getName());
            me.setRoleLabel(role.getLabel());
        }
        me.setName(employee.getEmployeeName());
        me.setEmail(employee.getEmail());
        me.setMobile(employee.getMobile());
        me.setEmployeeId(employee.getEmployeeId());
        me.setDepartment(employee.getDepartment());
        me.setDesignation(employee.getDesignation());
        me.setBranch(employee.getLocation());
        me.setProfilePhotoUrl(employee.getProfilePhotoUrl());
        me.setMustChangePassword(employee.isMustChangePassword());
        me.setPermissions(permissionCodes(role));
        return me;
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
        auditLogService.record("EMPLOYEE", empId, "PASSWORD_CHANGED", "Employee changed their own password");
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
        employee.setJoiningDate(request.getJoiningDate());
        employee.setManager(request.getManager());
        employee.setRole("EMPLOYEE");
        employee.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        employee.setMustChangePassword(true);  // force change on first login
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setLoginEnabled(true);

        log.info("Created employee: {}", empId);
        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", empId, "CREATED",
                "Created employee " + employee.getEmployeeName() + " (" + empId + ")");
        return saved;
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

        String oldEmployeeId = employee.getEmployeeId(); // capture BEFORE overwriting
        String newEmployeeId = request.getEmployeeId().trim().toUpperCase();

        // Guard against renaming into an ID that's already taken by someone else.
        if (!newEmployeeId.equalsIgnoreCase(oldEmployeeId)
                && employeeRepository.existsByEmployeeId(newEmployeeId)) {
            throw new DuplicateResourceException("Employee ID already in use: " + newEmployeeId);
        }

        employee.setEmployeeId(newEmployeeId);
        employee.setEmployeeName(request.getEmployeeName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setLocation(request.getLocation());
        employee.setJoiningDate(request.getJoiningDate());
        employee.setManager(request.getManager());

        Employee saved = employeeRepository.save(employee);

        // CASCADE: if the employeeId actually changed, every asset that was
        // linked to the OLD id must be repointed at the NEW one — otherwise
        // those assets silently orphan (they'll still show the employee's
        // name on the Assets page, but vanish from that employee's "View
        // Assets" panel, since that lookup matches strictly on employeeId).
        if (oldEmployeeId != null && !oldEmployeeId.isBlank()
                && !newEmployeeId.equalsIgnoreCase(oldEmployeeId)) {
            List<Asset> ownedAssets = assetRepository.findByEmployeeId(oldEmployeeId);
            for (Asset asset : ownedAssets) {
                asset.setEmployeeId(newEmployeeId);
            }
            if (!ownedAssets.isEmpty()) {
                assetRepository.saveAll(ownedAssets);
                log.info("Employee ID changed {} -> {}: relinked {} asset(s) to the new ID.",
                        oldEmployeeId, newEmployeeId, ownedAssets.size());
            }
        }

        // CASCADE: the Employee table is the single source of truth for name,
        // role, and — critically — location. Whenever any of those change
        // here (not just on an employeeId rename), every asset currently
        // assigned to this person must be updated to match, or the Employees
        // page and the Asset Inventory page will disagree about where that
        // person (and their equipment) is located until the asset happens to
        // be reassigned again.
        List<Asset> currentlyAssigned = assetRepository.findByEmployeeId(newEmployeeId);
        String syncedRole = (saved.getDesignation() != null && !saved.getDesignation().isBlank())
                ? saved.getDesignation()
                : saved.getRole();
        boolean anyChanged = false;
        for (Asset asset : currentlyAssigned) {
            boolean changed = false;
            if (!java.util.Objects.equals(asset.getEmployeeName(), saved.getEmployeeName())) {
                asset.setEmployeeName(saved.getEmployeeName());
                changed = true;
            }
            if (!java.util.Objects.equals(asset.getEmployeeRole(), syncedRole)) {
                asset.setEmployeeRole(syncedRole);
                changed = true;
            }
            if (!java.util.Objects.equals(asset.getLocation(), saved.getLocation())) {
                asset.setLocation(saved.getLocation());
                changed = true;
            }
            anyChanged = anyChanged || changed;
        }
        if (!currentlyAssigned.isEmpty()) {
            assetRepository.saveAll(currentlyAssigned);
        }
        if (anyChanged) {
            log.info("Employee {} updated: synced name/role/location onto {} assigned asset(s).",
                    newEmployeeId, currentlyAssigned.size());
            auditLogService.record("EMPLOYEE", newEmployeeId, "SYNCED_ASSETS",
                    "Propagated updated name/role/location to " + currentlyAssigned.size()
                            + " asset(s) assigned to " + saved.getEmployeeName());
        }

        auditLogService.record("EMPLOYEE", newEmployeeId, "UPDATED", "Updated employee " + saved.getEmployeeName() + " (" + newEmployeeId + ")");
        return saved;
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        log.warn("Deleting employee id={}", id);
        employeeRepository.deleteById(id);
        auditLogService.record("EMPLOYEE", employee.getEmployeeId(), "DELETED",
                "Deleted employee " + employee.getEmployeeName() + " (" + employee.getEmployeeId() + ")");
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
        auditLogService.record("EMPLOYEE", employeeId, "PASSWORD_RESET", "Admin reset password to default");
    }

    /** Returns all assets currently assigned to this employee. */
    @Transactional(readOnly = true)
    public List<Asset> getAssetsForEmployee(String employeeId) {
        return assetRepository.findByEmployeeId(employeeId.trim().toUpperCase());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EMPLOYEE SEPARATION / RESIGNATION WORKFLOW
    //  Active → Notice Period → Exit Clearance → Assets Returned → Resigned
    //  Employees are NEVER deleted as part of this flow — only their
    //  employmentStatus and separation fields change, so full employment
    //  history remains queryable forever.
    // ═════════════════════════════════════════════════════════════════════

    /** Full separation detail for the Employee Separation Modal / Separation History section. */
    @Transactional(readOnly = true)
    public EmployeeSeparationDetailDTO getSeparationDetail(String employeeId) {
        Employee employee = getByEmployeeId(employeeId);
        List<Asset> assigned = assetRepository.findByEmployeeId(employee.getEmployeeId()).stream()
                .filter(a -> "Assigned".equals(a.getAssetStatus()))
                .collect(Collectors.toList());
        List<Asset> returned = assetRepository.findByLastEmployeeIdAndEmployeeIdIsNull(employee.getEmployeeId());
        return EmployeeSeparationDetailDTO.from(employee, assigned, returned);
    }

    /** Step 1: Active → Notice Period. Kicks off the resignation and notifies HR. */
    @Transactional
    public EmployeeSeparationDetailDTO initiateSeparation(String employeeId, InitiateSeparationRequest request) {
        Employee employee = getByEmployeeId(employeeId);

        if (EmploymentStatus.RESIGNED.equals(employee.getEmploymentStatus())
                || EmploymentStatus.TERMINATED.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException(
                    "This employee has already left the organization. Reactivate them first if you need to restart separation.");
        }
        if (!EmploymentStatus.ACTIVE.equals(employee.getEmploymentStatus())
                && !EmploymentStatus.ON_LEAVE.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException(
                    "A separation is already in progress for this employee (status: " + employee.getEmploymentStatus() + ").");
        }

        employee.setEmploymentStatus(EmploymentStatus.NOTICE_PERIOD);
        employee.setNoticeStartDate(request.getNoticeStartDate());
        employee.setLastWorkingDate(request.getLastWorkingDate());
        employee.setResignationReason(request.getResignationReason());
        employee.setNoticePeriodDays(request.getNoticePeriodDays());
        employee.setSeparationRemarks(request.getRemarks());
        employee.setExitClearanceStatus(EmploymentStatus.CLEARANCE_PENDING);
        employee.setClearanceCompletionDate(null);
        employee.setResignedDate(null);
        employee.setUpdatedBy(currentAdminUsername());
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());

        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "SEPARATION_INITIATED",
                "Resignation started for " + saved.getEmployeeName() + " — reason: " + request.getResignationReason()
                        + ", last working date: " + request.getLastWorkingDate());
        separationNotificationService.notifySeparationStarted(saved);

        return getSeparationDetail(saved.getEmployeeId());
    }

    /** Step 2: Notice Period → Exit Clearance. Notifies IT to collect any still-assigned assets. */
    @Transactional
    public EmployeeSeparationDetailDTO moveToExitClearance(String employeeId, SeparationRemarksRequest request) {
        Employee employee = getByEmployeeId(employeeId);

        if (!EmploymentStatus.NOTICE_PERIOD.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException(
                    "Employee must be in Notice Period to move to Exit Clearance (current status: "
                            + employee.getEmploymentStatus() + ").");
        }

        employee.setEmploymentStatus(EmploymentStatus.EXIT_CLEARANCE);
        if (request != null && request.getRemarks() != null && !request.getRemarks().isBlank()) {
            employee.setSeparationRemarks(request.getRemarks());
        }
        Employee saved = employeeRepository.save(employee);

        long pendingAssets = assetRepository.countByAssetStatusAndEmployeeId("Assigned", saved.getEmployeeId());
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "SEPARATION_EXIT_CLEARANCE",
                "Moved to Exit Clearance for " + saved.getEmployeeName() + " — " + pendingAssets + " asset(s) pending return");
        separationNotificationService.notifyAssetCollectionRequired(saved, (int) pendingAssets);

        return getSeparationDetail(saved.getEmployeeId());
    }

    /**
     * Step 3 (validation gate): attempts to finalize the resignation. BLOCKED with a
     * {@link PendingAssetReturnException} listing every still-assigned asset if any
     * remain — per spec, resignation cannot complete until all assets are returned.
     * On success, moves the employee through Assets Returned → Resigned in one step
     * (both flags reflect reality the instant the asset check passes) and notifies Admin.
     */
    @Transactional
    public EmployeeSeparationDetailDTO completeResignation(String employeeId, SeparationRemarksRequest request) {
        Employee employee = getByEmployeeId(employeeId);

        if (EmploymentStatus.RESIGNED.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("This employee is already marked Resigned.");
        }
        if (EmploymentStatus.ACTIVE.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("Separation has not been initiated for this employee yet.");
        }

        List<Asset> stillAssigned = assetRepository.findByEmployeeId(employee.getEmployeeId()).stream()
                .filter(a -> "Assigned".equals(a.getAssetStatus()))
                .collect(Collectors.toList());
        if (!stillAssigned.isEmpty()) {
            throw new PendingAssetReturnException(stillAssigned);
        }

        String today = LocalDate.now().toString();
        employee.setEmploymentStatus(EmploymentStatus.ASSETS_RETURNED);
        employee.setExitClearanceStatus(EmploymentStatus.CLEARANCE_COMPLETED);
        employee.setClearanceCompletionDate(today);
        if (request != null && request.getRemarks() != null && !request.getRemarks().isBlank()) {
            employee.setSeparationRemarks(request.getRemarks());
        }
        employeeRepository.save(employee);
        separationNotificationService.notifyClearanceComplete(employee);

        employee.setEmploymentStatus(EmploymentStatus.RESIGNED);
        employee.setResignedDate(today);
        employee.setLoginEnabled(false);
        employee.setUpdatedBy(currentAdminUsername());
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());
        Employee saved = employeeRepository.save(employee);

        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "SEPARATION_COMPLETED",
                "Resignation finalized for " + saved.getEmployeeName() + " as of " + today
                        + " — all assets confirmed returned. Login access disabled.");
        separationNotificationService.notifyResignationFinalized(saved);

        return getSeparationDetail(saved.getEmployeeId());
    }

    /** Best-effort admin username for audit fields on lifecycle transitions triggered without an explicit caller. */
    private String currentAdminUsername() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Cancels an in-progress separation and restores the employee to Active (e.g. resignation withdrawn). */
    @Transactional
    public EmployeeSeparationDetailDTO cancelSeparation(String employeeId, SeparationRemarksRequest request) {
        Employee employee = getByEmployeeId(employeeId);

        if (EmploymentStatus.ACTIVE.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("This employee is already Active — there is no separation to cancel.");
        }
        if (EmploymentStatus.RESIGNED.equals(employee.getEmploymentStatus())
                || EmploymentStatus.TERMINATED.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException(
                    "This employee has already left the organization. Use Reactivate instead if they are rejoining.");
        }

        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setNoticeStartDate(null);
        employee.setLastWorkingDate(null);
        employee.setResignationReason(null);
        employee.setNoticePeriodDays(null);
        employee.setExitClearanceStatus(EmploymentStatus.CLEARANCE_PENDING);
        employee.setClearanceCompletionDate(null);
        employee.setResignedDate(null);
        employee.setSeparationRemarks(request != null ? request.getRemarks() : null);

        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "SEPARATION_CANCELLED",
                "Separation cancelled for " + saved.getEmployeeName() + " — restored to Active");

        return getSeparationDetail(saved.getEmployeeId());
    }

    /** Dashboard widgets: Notice Period / Pending Exit Clearance / Resigned This Month / Pending Asset Returns. */
    @Transactional(readOnly = true)
    public Map<String, Long> getSeparationDashboardStats() {
        YearMonth thisMonth = YearMonth.now();
        String monthStart = thisMonth.atDay(1).toString();
        String monthEnd = thisMonth.atEndOfMonth().toString();

        return Map.of(
                "noticePeriod", employeeRepository.countByEmploymentStatus(EmploymentStatus.NOTICE_PERIOD),
                "pendingExitClearance", employeeRepository.countPendingExitClearance(),
                "resignedThisMonth", employeeRepository.countResignedBetween(monthStart, monthEnd),
                "pendingAssetReturns", assetRepository.countPendingAssetReturnsAcrossSeparatingEmployees()
        );
    }

    /** Every employee currently anywhere in the separation pipeline (or already Resigned) — feeds the Employee Exit Report. */
    @Transactional(readOnly = true)
    public List<Employee> getAllInSeparation() {
        return employeeRepository.findAllInSeparation();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  ON LEAVE
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public Employee markOnLeave(String employeeId, com.vikkash.assetmanagementv1.dto.LeaveRequest request, String adminUsername) {
        Employee employee = getByEmployeeId(employeeId);
        if (!EmploymentStatus.ACTIVE.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException(
                    "Only Active employees can be placed On Leave (current status: " + employee.getEmploymentStatus() + ").");
        }
        employee.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        employee.setLeaveReason(request.getReason());
        employee.setLeaveStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now().toString());
        employee.setLeaveEndDate(request.getEndDate());
        if (request.getRemarks() != null && !request.getRemarks().isBlank()) {
            employee.setSeparationRemarks(request.getRemarks());
        }
        employee.setUpdatedBy(adminUsername);
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());
        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "MARKED_ON_LEAVE",
                saved.getEmployeeName() + " placed On Leave — reason: " + request.getReason());
        return saved;
    }

    @Transactional
    public Employee endLeave(String employeeId, String adminUsername) {
        Employee employee = getByEmployeeId(employeeId);
        if (!EmploymentStatus.ON_LEAVE.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("This employee is not currently On Leave.");
        }
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setUpdatedBy(adminUsername);
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());
        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "LEAVE_ENDED",
                saved.getEmployeeName() + " returned from leave — restored to Active");
        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TERMINATION (involuntary exit)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Marks an employee Terminated. Never deletes the record. Blocked with a
     * {@link PendingAssetReturnException} if assets are still assigned — same
     * guard as resignation, so IT reclaims equipment before offboarding is final.
     */
    @Transactional
    public Employee terminateEmployee(String employeeId, com.vikkash.assetmanagementv1.dto.TerminateEmployeeRequest request, String adminUsername) {
        Employee employee = getByEmployeeId(employeeId);
        if (EmploymentStatus.RESIGNED.equals(employee.getEmploymentStatus())
                || EmploymentStatus.TERMINATED.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("This employee has already left the organization.");
        }

        List<Asset> stillAssigned = assetRepository.findByEmployeeId(employee.getEmployeeId()).stream()
                .filter(a -> "Assigned".equals(a.getAssetStatus()))
                .collect(Collectors.toList());
        if (!stillAssigned.isEmpty()) {
            throw new PendingAssetReturnException(stillAssigned);
        }

        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        employee.setTerminationDate(request.getTerminationDate());
        employee.setLastWorkingDate(request.getTerminationDate());
        employee.setResignationReason(request.getExitReason());
        employee.setSeparationRemarks(request.getExitRemarks());
        employee.setExitClearanceStatus(EmploymentStatus.CLEARANCE_COMPLETED);
        employee.setClearanceCompletionDate(LocalDate.now().toString());
        employee.setLoginEnabled(false);
        employee.setUpdatedBy(adminUsername);
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());

        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "TERMINATED",
                saved.getEmployeeName() + " terminated as of " + request.getTerminationDate()
                        + " — reason: " + request.getExitReason() + ". Login access disabled.");
        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REACTIVATE (Resigned / Terminated -> Active — rejoining)
    // ═════════════════════════════════════════════════════════════════════

    /** Restores a Resigned/Terminated employee to Active and re-enables login. Historical separation fields are preserved, not wiped. */
    @Transactional
    public Employee reactivateEmployee(String employeeId, String adminUsername) {
        Employee employee = getByEmployeeId(employeeId);
        if (!EmploymentStatus.RESIGNED.equals(employee.getEmploymentStatus())
                && !EmploymentStatus.TERMINATED.equals(employee.getEmploymentStatus())) {
            throw new IllegalArgumentException("Only Resigned or Terminated employees can be reactivated.");
        }
        String previousStatus = employee.getEmploymentStatus();
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setLoginEnabled(true);
        employee.setUpdatedBy(adminUsername);
        employee.setUpdatedDate(java.time.LocalDateTime.now().toString());
        Employee saved = employeeRepository.save(employee);
        auditLogService.record("EMPLOYEE", saved.getEmployeeId(), "REACTIVATED",
                saved.getEmployeeName() + " reactivated from " + previousStatus + " to Active. Login access restored.");
        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  STATUS FILTERS / DASHBOARD / RESIGNED VIEW
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Employees Module filter tabs: Active / Notice Period / Resigned /
     * Terminated / All. "Notice Period" also includes the legacy Exit
     * Clearance / Assets Returned sub-stages so nothing falls through the
     * cracks for employees mid-separation.
     */
    @Transactional(readOnly = true)
    public List<Employee> getByStatusFilter(String filter) {
        if (filter == null || filter.isBlank() || "All".equalsIgnoreCase(filter)) {
            return employeeRepository.findAll();
        }
        if (EmploymentStatus.NOTICE_PERIOD.equalsIgnoreCase(filter)) {
            return employeeRepository.findByEmploymentStatusIn(EmploymentStatus.NOTICE_PERIOD_BUCKET);
        }
        for (String status : EmploymentStatus.ALL_STATUSES) {
            if (status.equalsIgnoreCase(filter)) {
                return employeeRepository.findByEmploymentStatus(status);
            }
        }
        throw new IllegalArgumentException("Unknown status filter: " + filter);
    }

    @Transactional(readOnly = true)
    public List<ResignedEmployeeViewDTO> getResignedEmployeesView() {
        return employeeRepository.findAllResigned().stream()
                .map(ResignedEmployeeViewDTO::from)
                .collect(Collectors.toList());
    }

    /** Dashboard cards: Active / On Leave / Notice Period / Resigned / Terminated / Pending Returns / Joined & Left This Month. */
    @Transactional(readOnly = true)
    public Map<String, Long> getLifecycleDashboardStats() {
        YearMonth thisMonth = YearMonth.now();
        String monthStart = thisMonth.atDay(1).toString();
        String monthEnd = thisMonth.atEndOfMonth().toString();

        Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("active", employeeRepository.countByEmploymentStatus(EmploymentStatus.ACTIVE));
        stats.put("onLeave", employeeRepository.countByEmploymentStatus(EmploymentStatus.ON_LEAVE));
        stats.put("noticePeriod", employeeRepository.countByEmploymentStatusIn(EmploymentStatus.NOTICE_PERIOD_BUCKET));
        stats.put("resigned", employeeRepository.countByEmploymentStatus(EmploymentStatus.RESIGNED));
        stats.put("terminated", employeeRepository.countByEmploymentStatus(EmploymentStatus.TERMINATED));
        stats.put("pendingAssetReturns", assetRepository.countPendingAssetReturnsAcrossSeparatingEmployees());
        stats.put("joinedThisMonth", employeeRepository.countJoinedBetween(monthStart, monthEnd));
        stats.put("leftThisMonth", employeeRepository.countLeftBetween(monthStart, monthEnd));
        return stats;
    }
}
