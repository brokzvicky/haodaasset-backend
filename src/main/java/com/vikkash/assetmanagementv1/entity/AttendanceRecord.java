package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single fingerprint punch pushed by an eSSL biometric device in ADMS
 * mode. This is the persistent record backing the Attendance Management
 * module's live feed, history, and reports.
 *
 * The (device_serial_number, device_user_id, punch_time) triple is unique:
 * ADMS devices keep resending buffered logs until the server acknowledges
 * with "OK", so the same punch can legitimately arrive more than once
 * (e.g. if our response was lost on a flaky network). We de-duplicate on
 * that triple rather than trusting the device to only send once.
 */
@Entity
@Table(
    name = "attendance_record",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_attendance_dedupe",
        columnNames = {"device_serial_number", "device_user_id", "punch_time"}),
    indexes = {
        @Index(name = "idx_attendance_employee_id", columnList = "employee_id"),
        @Index(name = "idx_attendance_punch_time",  columnList = "punch_time")
    }
)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PIN / user ID as enrolled on the biometric device (raw, unresolved). */
    @Column(name = "device_user_id", nullable = false, length = 30)
    private String deviceUserId;

    /** Resolved via AttendanceDeviceMapping + Employee at ingest time; null if the PIN isn't mapped to an employee yet. */
    @Column(name = "employee_id", length = 20)
    private String employeeId;

    /** Snapshot of the employee's name at punch time, so historical rows still display correctly even if the employee is later renamed or offboarded. */
    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    /** Snapshot of the employee's department at punch time, if known — used for department-wise attendance filtering/reporting. */
    @Column(name = "department", length = 100)
    private String department;

    /** Timestamp the fingerprint was actually scanned on the device (device clock, not server clock). */
    @Column(name = "punch_time", nullable = false)
    private LocalDateTime punchTime;

    /** IN, OUT, or UNKNOWN when the device doesn't report a clear direction. */
    @Column(name = "punch_type", nullable = false, length = 10)
    private String punchType;

    /** Human-readable verify method, e.g. "Fingerprint", "Card", "Password", decoded from the device's numeric code. */
    @Column(name = "verify_mode", length = 30)
    private String verifyMode;

    @Column(name = "device_serial_number", nullable = false, length = 50)
    private String deviceSerialNumber;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /** RECEIVED / UNMAPPED — kept as a field (rather than hardcoded) so future statuses slot in without a migration. */
    @Column(nullable = false, length = 20)
    private String status = "RECEIVED";

    /** The raw tab-separated line the device sent, kept for debugging/audit — never shown in the UI. */
    @Column(name = "raw_line", length = 500)
    private String rawLine;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceUserId() { return deviceUserId; }
    public void setDeviceUserId(String deviceUserId) { this.deviceUserId = deviceUserId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDateTime getPunchTime() { return punchTime; }
    public void setPunchTime(LocalDateTime punchTime) { this.punchTime = punchTime; }

    public String getPunchType() { return punchType; }
    public void setPunchType(String punchType) { this.punchType = punchType; }

    public String getVerifyMode() { return verifyMode; }
    public void setVerifyMode(String verifyMode) { this.verifyMode = verifyMode; }

    public String getDeviceSerialNumber() { return deviceSerialNumber; }
    public void setDeviceSerialNumber(String deviceSerialNumber) { this.deviceSerialNumber = deviceSerialNumber; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
