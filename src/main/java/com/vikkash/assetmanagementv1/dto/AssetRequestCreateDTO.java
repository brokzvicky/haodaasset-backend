package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

public class AssetRequestCreateDTO {

    @NotBlank(message = "Asset type is required")
    private String assetType;

    private String urgency = "Normal";

    @NotBlank(message = "Reason is required")
    private String reason;

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
