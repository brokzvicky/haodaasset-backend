package com.vikkash.assetmanagementv1.dto;

/**
 * Unified login response for both /api/auth/admin/login and
 * /api/auth/employee/login. Fields that don't apply to a role are left null
 * (e.g. employeeId is null for admin logins).
 */
public class LoginResponse {

    private String token;
    private String role;          // ADMIN | EMPLOYEE
    private String name;          // display name shown in sidebar/topbar
    private String employeeId;    // null for admin
    private String email;         // null for admin
    private String department;    // null for admin
    private String designation;   // null for admin
    private String location;      // null for admin
    private boolean mustChangePassword; // always false for admin

    public LoginResponse() {
    }

    public static LoginResponse forAdmin(String token, String username) {
        LoginResponse r = new LoginResponse();
        r.token = token;
        r.role = "ADMIN";
        r.name = username;
        r.mustChangePassword = false;
        return r;
    }

    public static LoginResponse forEmployee(String token, com.vikkash.assetmanagementv1.entity.Employee emp) {
        LoginResponse r = new LoginResponse();
        r.token = token;
        r.role = "EMPLOYEE";
        r.name = emp.getEmployeeName();
        r.employeeId = emp.getEmployeeId();
        r.email = emp.getEmail();
        r.department = emp.getDepartment();
        r.designation = emp.getDesignation();
        r.location = emp.getLocation();
        r.mustChangePassword = emp.isMustChangePassword();
        return r;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
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

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }
}
