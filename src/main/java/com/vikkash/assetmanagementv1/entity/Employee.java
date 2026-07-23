package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "employee", uniqueConstraints = @UniqueConstraint(columnNames = "employee_id"))public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business-facing login identifier, e.g. EMP001 */
    @Column(name = "employee_id", nullable = false, unique = true, length = 20)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(unique = true)
    private String email;

    private String department;

    private String designation;

    private String location;

    /** BCrypt hash — never store plain text */
    @Column(nullable = false)
    private String password;

    /** ADMIN or EMPLOYEE — kept on the row for simple role checks */
    @Column(nullable = false, length = 20)
    private String role = "EMPLOYEE";

    /**
     * True until the employee changes their password away from the
     * organization default (Haoda@321). Drives the forced password-change flow.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    // ── Employment lifecycle / separation tracking ──────────────────────────
    // Historical employee records are NEVER deleted; instead the row moves
    // through this lifecycle so full employment history (including who left,
    // when, and why) stays queryable forever.
    //
    // Active -> Notice Period -> Exit Clearance -> Assets Returned -> Resigned
    @Column(name = "employment_status", length = 30, nullable = false,
            columnDefinition = "varchar(30) default 'Active'")
    private String employmentStatus = "Active";

    /** Date the employee joined the organization (yyyy-MM-dd string, matching the rest of the codebase's date convention). */
    @Column(name = "joining_date")
    private String joiningDate;

    /** Date the resignation notice period begins - set when separation is initiated. */
    @Column(name = "notice_start_date")
    private String noticeStartDate;

    /** The employee's final working day, as agreed at resignation time. */
    @Column(name = "last_working_date")
    private String lastWorkingDate;

    /** Notice period length in days, captured at initiation for reference even if dates are later adjusted. */
    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    /** Why the employee is leaving - dropdown-driven (e.g. "Better Opportunity", "Relocation", "Personal Reasons", "Retirement", "Termination", "Other"). */
    @Column(name = "resignation_reason", length = 100)
    private String resignationReason;

    /** Free-text HR remarks captured at any point in the separation workflow. */
    @Column(name = "separation_remarks", length = 1000)
    private String separationRemarks;

    /** "Pending" or "Completed" - whether IT/Admin exit clearance (asset return, access revocation, etc.) is done. */
    @Column(name = "exit_clearance_status", length = 20)
    private String exitClearanceStatus = "Pending";

    /** Date the exit clearance was fully completed (all assets returned + clearance signed off). */
    @Column(name = "clearance_completion_date")
    private String clearanceCompletionDate;

    /** Date the employee's status was finally set to Resigned. */
    @Column(name = "resigned_date")
    private String resignedDate;

    // ── Profile additions (role-based login / "load my full profile") ──────
    // All nullable so existing rows keep working unchanged until the
    // employee/HR fills these in.

    @Column(unique = true, length = 20)
    private String mobile;

    /** S3 object URL/key for the profile photo, or null if none uploaded. */
    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    // ── Fine-grained authorization ──────────────────────────────────────────
    // Deliberately separate from the existing coarse `role` String field
    // above (which stays "ADMIN"/"EMPLOYEE" exactly as-is — AssetService and
    // EmployeeService already branch on it and must not be disturbed). This
    // is the finer layer on top: which specific modules/actions this
    // particular employee is authorized for beyond standard self-service.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role roleRef;

    // ── Multi-channel login ─────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Google account's stable "sub" claim, set only for Google-linked accounts. */
    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    public Employee() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(String joiningDate) {
        this.joiningDate = joiningDate;
    }

    public String getNoticeStartDate() {
        return noticeStartDate;
    }

    public void setNoticeStartDate(String noticeStartDate) {
        this.noticeStartDate = noticeStartDate;
    }

    public String getLastWorkingDate() {
        return lastWorkingDate;
    }

    public void setLastWorkingDate(String lastWorkingDate) {
        this.lastWorkingDate = lastWorkingDate;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
    }

    public String getResignationReason() {
        return resignationReason;
    }

    public void setResignationReason(String resignationReason) {
        this.resignationReason = resignationReason;
    }

    public String getSeparationRemarks() {
        return separationRemarks;
    }

    public void setSeparationRemarks(String separationRemarks) {
        this.separationRemarks = separationRemarks;
    }

    public String getExitClearanceStatus() {
        return exitClearanceStatus;
    }

    public void setExitClearanceStatus(String exitClearanceStatus) {
        this.exitClearanceStatus = exitClearanceStatus;
    }

    public String getClearanceCompletionDate() {
        return clearanceCompletionDate;
    }

    public void setClearanceCompletionDate(String clearanceCompletionDate) {
        this.clearanceCompletionDate = clearanceCompletionDate;
    }

    public String getResignedDate() {
        return resignedDate;
    }

    public void setResignedDate(String resignedDate) {
        this.resignedDate = resignedDate;
    }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public Role getRoleRef() { return roleRef; }
    public void setRoleRef(Role roleRef) { this.roleRef = roleRef; }

    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
}
