package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for PUT /assets/assign/{id}.
 *
 * employeeId is the only field the backend actually trusts to identify who
 * the asset is being assigned to. employeeName, employeeRole, and location
 * are accepted for backward compatibility with older/alternate callers but
 * are IGNORED by the service — AssetService.assignAsset looks the employee
 * up by employeeId and copies employeeName/employeeRole/location straight
 * from the Employee table, so the asset and the employee record can never
 * disagree about where that person (and therefore their equipment) is.
 */
public class AssignAssetRequest {

    @NotBlank(message = "Employee ID is required to assign an asset")
    private String employeeId;

    /** Accepted but ignored — the employee's current name is read from the Employee table instead. */
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
