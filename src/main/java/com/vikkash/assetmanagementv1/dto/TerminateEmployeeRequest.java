package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/admin/employees/{employeeId}/terminate */
public class TerminateEmployeeRequest {

    @NotBlank(message = "Termination date is required")
    private String terminationDate;

    @NotBlank(message = "Exit reason is required")
    private String exitReason;

    private String exitRemarks;

    public String getTerminationDate() { return terminationDate; }
    public void setTerminationDate(String terminationDate) { this.terminationDate = terminationDate; }

    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }

    public String getExitRemarks() { return exitRemarks; }
    public void setExitRemarks(String exitRemarks) { this.exitRemarks = exitRemarks; }
}
