package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Used by PUT /api/admin/employees/{id} to edit an existing employee's profile fields (not password). */
public class EmployeeUpdateRequest {
    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }
    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @Email(message = "Email must be valid")
    private String email;

    private String department;

    private String designation;

    private String location;

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

    private String employeeId;

    public void setLocation(String location) {
        this.location = location;
    }

}
