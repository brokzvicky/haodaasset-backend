package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/admin/employees/{employeeId}/separation/initiate */
public class InitiateSeparationRequest {

    @NotBlank(message = "Notice start date is required")
    private String noticeStartDate;

    @NotBlank(message = "Last working date is required")
    private String lastWorkingDate;

    @NotBlank(message = "Resignation reason is required")
    private String resignationReason;

    private Integer noticePeriodDays;

    private String remarks;

    public String getNoticeStartDate() { return noticeStartDate; }
    public void setNoticeStartDate(String noticeStartDate) { this.noticeStartDate = noticeStartDate; }

    public String getLastWorkingDate() { return lastWorkingDate; }
    public void setLastWorkingDate(String lastWorkingDate) { this.lastWorkingDate = lastWorkingDate; }

    public String getResignationReason() { return resignationReason; }
    public void setResignationReason(String resignationReason) { this.resignationReason = resignationReason; }

    public Integer getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(Integer noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
