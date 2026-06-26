package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for PUT /assets/assign/{id}.
 * employeeName is the minimum required field; the rest add audit context.
 */
public class AssignAssetRequest {

    private String employeeId;

    @NotBlank(message = "Employee name is required for assignment")
    private String employeeName;

    private String employeeRole;
    private String location;
    private String assignedDate;
    private String remarks;

    public String getEmployeeId()   { return employeeId; }
    public void setEmployeeId(String v) { this.employeeId = v; }

    public String getEmployeeName()   { return employeeName; }
    public void setEmployeeName(String v) { this.employeeName = v; }

    public String getEmployeeRole()   { return employeeRole; }
    public void setEmployeeRole(String v) { this.employeeRole = v; }

    public String getLocation()   { return location; }
    public void setLocation(String v) { this.location = v; }

    public String getAssignedDate()   { return assignedDate; }
    public void setAssignedDate(String v) { this.assignedDate = v; }

    public String getRemarks()   { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
}
