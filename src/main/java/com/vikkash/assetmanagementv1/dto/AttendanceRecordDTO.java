package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.AttendanceRecord;

import java.time.LocalDateTime;

/**
 * What the frontend actually receives — deliberately narrower than the
 * entity (no raw device line noise) and used for both the REST list and
 * the SSE live-stream payload.
 */
public class AttendanceRecordDTO {

    private Long id;
    private String employeeId;
    private String employeeName;
    private String department;
    private LocalDateTime punchTime;
    private String punchType;
    private String verifyMode;
    private String deviceName;
    private String status;

    public static AttendanceRecordDTO from(AttendanceRecord record) {
        AttendanceRecordDTO dto = new AttendanceRecordDTO();
        dto.id = record.getId();
        dto.employeeId = record.getEmployeeId() != null ? record.getEmployeeId() : record.getDeviceUserId();
        dto.employeeName = record.getEmployeeName();
        dto.department = record.getDepartment();
        dto.punchTime = record.getPunchTime();
        dto.punchType = record.getPunchType();
        dto.verifyMode = record.getVerifyMode();
        dto.deviceName = record.getDeviceName();
        dto.status = record.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getDepartment() { return department; }
    public LocalDateTime getPunchTime() { return punchTime; }
    public String getPunchType() { return punchType; }
    public String getVerifyMode() { return verifyMode; }
    public String getDeviceName() { return deviceName; }
    public String getStatus() { return status; }
}
