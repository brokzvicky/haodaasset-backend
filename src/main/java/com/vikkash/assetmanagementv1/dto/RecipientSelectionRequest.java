package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for POST /api/admin/filecenter/recipients/preview — lets the
 * upload dialog show "145 Employees" in the Share File confirmation modal
 * before anything is actually uploaded.
 */
public class RecipientSelectionRequest {

    /** ALL, DEPARTMENT, LOCATION, INDIVIDUAL, MULTIPLE, ASSET_OWNERS */
    @NotBlank(message = "recipientType is required")
    private String recipientType;

    /** Department names, location names, or employee IDs — meaning depends on recipientType. Ignored for ALL/ASSET_OWNERS. */
    private List<String> recipientValues;

    public RecipientSelectionRequest() {
    }

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public List<String> getRecipientValues() { return recipientValues; }
    public void setRecipientValues(List<String> recipientValues) { this.recipientValues = recipientValues; }
}
