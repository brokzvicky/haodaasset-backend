package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AssetRequestStatusDTO {

    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED|PENDING", message = "Status must be APPROVED, REJECTED, or PENDING")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
