package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST/PUT /api/admin/attendance/mappings — links a device PIN to an existing employeeId. */
public class AttendanceMappingRequest {

    @NotBlank(message = "Device PIN is required")
    private String devicePin;

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    public String getDevicePin() { return devicePin; }
    public void setDevicePin(String devicePin) { this.devicePin = devicePin; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
}
