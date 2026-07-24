package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;

import java.util.List;

/**
 * Everything the Employee Separation Modal / Separation History section
 * needs in one round trip: employee profile fields, current workflow
 * status, every date/reason captured so far, and a live split of the
 * employee's assets into "still assigned" vs "already returned" so the
 * frontend never has to reconcile two separate asset lists itself.
 */
public class EmployeeSeparationDetailDTO {

    private String employeeId;
    private String employeeName;
    private String email;
    private String department;
    private String designation;
    private String location;
    private String joiningDate;

    private String employmentStatus;
    private String noticeStartDate;
    private String lastWorkingDate;
    private Integer noticePeriodDays;
    private String resignationReason;
    private String separationRemarks;
    private String exitClearanceStatus;
    private String clearanceCompletionDate;
    private String resignedDate;
    private String manager;
    private boolean loginEnabled;
    private String terminationDate;
    private String leaveReason;
    private String leaveStartDate;
    private String leaveEndDate;
    private String updatedBy;
    private String updatedDate;

    private List<Asset> assignedAssets;
    private List<Asset> returnedAssets;

    public static EmployeeSeparationDetailDTO from(Employee e, List<Asset> assignedAssets, List<Asset> returnedAssets) {
        EmployeeSeparationDetailDTO dto = new EmployeeSeparationDetailDTO();
        dto.employeeId = e.getEmployeeId();
        dto.employeeName = e.getEmployeeName();
        dto.email = e.getEmail();
        dto.department = e.getDepartment();
        dto.designation = e.getDesignation();
        dto.location = e.getLocation();
        dto.joiningDate = e.getJoiningDate();
        dto.employmentStatus = e.getEmploymentStatus();
        dto.noticeStartDate = e.getNoticeStartDate();
        dto.lastWorkingDate = e.getLastWorkingDate();
        dto.noticePeriodDays = e.getNoticePeriodDays();
        dto.resignationReason = e.getResignationReason();
        dto.separationRemarks = e.getSeparationRemarks();
        dto.exitClearanceStatus = e.getExitClearanceStatus();
        dto.clearanceCompletionDate = e.getClearanceCompletionDate();
        dto.resignedDate = e.getResignedDate();
        dto.manager = e.getManager();
        dto.loginEnabled = e.isLoginEnabled();
        dto.terminationDate = e.getTerminationDate();
        dto.leaveReason = e.getLeaveReason();
        dto.leaveStartDate = e.getLeaveStartDate();
        dto.leaveEndDate = e.getLeaveEndDate();
        dto.updatedBy = e.getUpdatedBy();
        dto.updatedDate = e.getUpdatedDate();
        dto.assignedAssets = assignedAssets;
        dto.returnedAssets = returnedAssets;
        return dto;
    }

    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getDesignation() { return designation; }
    public String getLocation() { return location; }
    public String getJoiningDate() { return joiningDate; }
    public String getEmploymentStatus() { return employmentStatus; }
    public String getNoticeStartDate() { return noticeStartDate; }
    public String getLastWorkingDate() { return lastWorkingDate; }
    public Integer getNoticePeriodDays() { return noticePeriodDays; }
    public String getResignationReason() { return resignationReason; }
    public String getSeparationRemarks() { return separationRemarks; }
    public String getExitClearanceStatus() { return exitClearanceStatus; }
    public String getClearanceCompletionDate() { return clearanceCompletionDate; }
    public String getResignedDate() { return resignedDate; }
    public String getManager() { return manager; }
    public boolean isLoginEnabled() { return loginEnabled; }
    public String getTerminationDate() { return terminationDate; }
    public String getLeaveReason() { return leaveReason; }
    public String getLeaveStartDate() { return leaveStartDate; }
    public String getLeaveEndDate() { return leaveEndDate; }
    public String getUpdatedBy() { return updatedBy; }
    public String getUpdatedDate() { return updatedDate; }
    public List<Asset> getAssignedAssets() { return assignedAssets; }
    public List<Asset> getReturnedAssets() { return returnedAssets; }
}
