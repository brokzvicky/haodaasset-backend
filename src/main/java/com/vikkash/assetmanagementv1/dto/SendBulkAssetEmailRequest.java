package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Request body for POST /api/admin/asset-email/send. */
public class SendBulkAssetEmailRequest {

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    @NotEmpty(message = "Select at least one asset to include in the email")
    private List<Long> assetIds;

    public SendBulkAssetEmailRequest() {
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public List<Long> getAssetIds() { return assetIds; }
    public void setAssetIds(List<Long> assetIds) { this.assetIds = assetIds; }
}
