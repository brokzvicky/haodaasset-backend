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
}
