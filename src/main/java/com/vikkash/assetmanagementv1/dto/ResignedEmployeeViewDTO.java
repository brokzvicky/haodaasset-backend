package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Employee;

/**
 * Row shape for the dedicated "Resigned Employees" view: Employee ID, Name,
 * Department, Designation, Manager, Joining Date, Last Working Date,
 * Resignation Date, Exit Reason, and Asset Return Status — one round trip,
 * no client-side reconciliation needed.
 */
public class ResignedEmployeeViewDTO {

    private String employeeId;
    private String employeeName;
    private String department;
    private String designation;
    private String manager;
    private String joiningDate;
    private String lastWorkingDate;
    private String resignationDate;
    private String exitReason;
    private String exitRemarks;
    private String assetReturnStatus;
    private String employmentStatus;

    public static ResignedEmployeeViewDTO from(Employee e) {
        ResignedEmployeeViewDTO dto = new ResignedEmployeeViewDTO();
        dto.employeeId = e.getEmployeeId();
        dto.employeeName = e.getEmployeeName();
        dto.department = e.getDepartment();
        dto.designation = e.getDesignation();
        dto.manager = e.getManager();
        dto.joiningDate = e.getJoiningDate();
        dto.lastWorkingDate = e.getLastWorkingDate();
        dto.resignationDate = e.getNoticeStartDate() != null ? e.getNoticeStartDate() : e.getResignedDate();
        dto.exitReason = e.getResignationReason();
        dto.exitRemarks = e.getSeparationRemarks();
        dto.assetReturnStatus = e.getExitClearanceStatus();
        dto.employmentStatus = e.getEmploymentStatus();
        return dto;
    }

    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getDepartment() { return department; }
    public String getDesignation() { return designation; }
    public String getManager() { return manager; }
    public String getJoiningDate() { return joiningDate; }
    public String getLastWorkingDate() { return lastWorkingDate; }
    public String getResignationDate() { return resignationDate; }
    public String getExitReason() { return exitReason; }
    public String getExitRemarks() { return exitRemarks; }
    public String getAssetReturnStatus() { return assetReturnStatus; }
    public String getEmploymentStatus() { return employmentStatus; }
}
