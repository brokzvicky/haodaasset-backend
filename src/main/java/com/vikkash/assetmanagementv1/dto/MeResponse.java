package com.vikkash.assetmanagementv1.dto;

import java.util.List;

/**
 * Response for {@code GET /api/auth/me} — the single source of truth the
 * frontend loads right after login (and on every app reload while a token
 * is still valid) to populate the whole authenticated session: display
 * identity, contact details, org placement, and the caller's resolved
 * permission set for module-level rendering.
 *
 * Fields that don't apply to a given account are left null (e.g.
 * {@code employeeId} for an admin).
 */
public class MeResponse {

    private String role;              // ADMIN | EMPLOYEE  (coarse — unchanged from today)
    private String roleName;          // fine-grained Role.name, e.g. "SYSTEM_ADMIN" (null if unassigned)
    private String roleLabel;         // fine-grained Role.label, e.g. "System Admin"

    private String name;
    private String email;
    private String mobile;
    private String employeeId;        // null for admin
    private String department;
    private String designation;
    private String branch;
    private String profilePhotoUrl;
    private boolean mustChangePassword;

    /** Fine-grained permission codes, e.g. ["ASSETS_VIEW","ASSETS_WRITE",...] — drives sidebar module visibility. */
    private List<String> permissions;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRoleLabel() { return roleLabel; }
    public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
