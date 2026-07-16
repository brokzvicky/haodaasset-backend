package com.vikkash.assetmanagementv1.dto;

import java.util.List;

/**
 * Request body for bulk asset operations (Assets page "select multiple, act
 * once" toolbar). Only the fields that should change need be supplied —
 * null fields are left untouched on each targeted asset.
 */
public class BulkAssetUpdateRequest {

    private List<Long> assetIds;
    private String assetStatus;
    private String location;
    private String assetCondition;
    private String remarks;

    public List<Long> getAssetIds() { return assetIds; }
    public void setAssetIds(List<Long> assetIds) { this.assetIds = assetIds; }

    public String getAssetStatus() { return assetStatus; }
    public void setAssetStatus(String assetStatus) { this.assetStatus = assetStatus; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAssetCondition() { return assetCondition; }
    public void setAssetCondition(String assetCondition) { this.assetCondition = assetCondition; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
