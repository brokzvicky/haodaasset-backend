package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/admin/employees/{employeeId}/leave/start */
public class LeaveRequest {

    @NotBlank(message = "Leave reason is required")
    private String reason;

    private String startDate;
    private String endDate;
    private String remarks;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
