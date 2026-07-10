package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Employee;

/**
 * Lightweight, password-free view of an employee for the "Send Asset Email"
 * search box and result card. Intentionally excludes password/role/must-change
 * fields — this DTO exists purely for lookup + display.
 */
public class EmployeeSearchResponse {

    private String employeeId;
    private String employeeName;
    private String email;
    private String department;
    private String designation;
    private String location;

    public EmployeeSearchResponse() {
    }

    public EmployeeSearchResponse(String employeeId, String employeeName, String email,
                                   String department, String designation, String location) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.email = email;
        this.department = department;
        this.designation = designation;
        this.location = location;
    }

    public static EmployeeSearchResponse from(Employee e) {
        return new EmployeeSearchResponse(
                e.getEmployeeId(), e.getEmployeeName(), e.getEmail(),
                e.getDepartment(), e.getDesignation(), e.getLocation());
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
