package com.vikkash.assetmanagementv1.dto;

/** GET /api/admin/attendance/mappings row — the raw mapping enriched with the employee's current name/department. */
public class AttendanceMappingResponse {

    private Long id;
    private String devicePin;
    private String employeeId;
    private String employeeName;
    private String department;
    /** False when the mapped employeeId no longer exists in HaodaAsset (e.g. was deleted) — flagged so admins can fix stale mappings. */
    private boolean employeeFound;

    public AttendanceMappingResponse(Long id, String devicePin, String employeeId,
                                      String employeeName, String department, boolean employeeFound) {
        this.id = id;
        this.devicePin = devicePin;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.employeeFound = employeeFound;
    }

    public Long getId() { return id; }
    public String getDevicePin() { return devicePin; }
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getDepartment() { return department; }
    public boolean isEmployeeFound() { return employeeFound; }
}
