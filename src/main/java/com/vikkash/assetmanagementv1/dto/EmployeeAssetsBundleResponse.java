package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.Asset;

import java.util.List;

/**
 * Response for GET /api/admin/asset-email/employee/{employeeId} — the
 * employee's directory details plus every asset currently assigned to them,
 * fetched together so the "Send Asset Email" page needs only one round trip
 * after the admin picks a search result.
 */
public class EmployeeAssetsBundleResponse {

    private EmployeeSearchResponse employee;
    private List<Asset> assets;

    public EmployeeAssetsBundleResponse() {
    }

    public EmployeeAssetsBundleResponse(EmployeeSearchResponse employee, List<Asset> assets) {
        this.employee = employee;
        this.assets = assets;
    }

    public EmployeeSearchResponse getEmployee() { return employee; }
    public void setEmployee(EmployeeSearchResponse employee) { this.employee = employee; }

    public List<Asset> getAssets() { return assets; }
    public void setAssets(List<Asset> assets) { this.assets = assets; }
}
