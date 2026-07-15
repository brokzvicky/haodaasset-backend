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

    /** "Permanent" or "Temporary". Defaults to "Permanent" when omitted. */
    private String assignmentType;

    /** Required when assignmentType = "Temporary": why the assignment is temporary. */
    private String temporaryReason;

    /** Required when assignmentType = "Temporary": how many days the laptop is assigned for. */
    private Integer temporaryDurationDays;

    /** Optional: any issues noted with the employee's previous/old asset. */
    private String oldAssetIssues;

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

    public String getAssignmentType()   { return assignmentType; }
    public void setAssignmentType(String v) { this.assignmentType = v; }

    public String getTemporaryReason()   { return temporaryReason; }
    public void setTemporaryReason(String v) { this.temporaryReason = v; }

    public Integer getTemporaryDurationDays()   { return temporaryDurationDays; }
    public void setTemporaryDurationDays(Integer v) { this.temporaryDurationDays = v; }

    public String getOldAssetIssues()   { return oldAssetIssues; }
    public void setOldAssetIssues(String v) { this.oldAssetIssues = v; }
}
