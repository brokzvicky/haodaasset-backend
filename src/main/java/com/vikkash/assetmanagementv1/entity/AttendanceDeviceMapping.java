package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

/**
 * Maps the numeric "PIN" / user ID enrolled on an eSSL biometric device to
 * an existing HaodaAsset {@link Employee} (via employeeId). The device
 * itself only ever reports back the PIN it was enrolled with (e.g. "1007")
 * — it does not know employee names, departments, or anything else — so
 * without this lookup table the attendance list would just show raw
 * device PINs.
 *
 * Unlike the original standalone eSSL Attendance POC (which duplicated
 * employee names into its own flat table), this mapping deliberately
 * stores only the link (devicePin -> employeeId) and always resolves the
 * live name/department/status from the existing Employee entity at read
 * time — so renaming or offboarding an employee in HaodaAsset is reflected
 * in attendance immediately, with nothing to keep in sync by hand.
 */
@Entity
@Table(name = "attendance_device_mapping", uniqueConstraints = @UniqueConstraint(columnNames = "device_pin"))
public class AttendanceDeviceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The PIN / user ID as enrolled on the eSSL device. */
    @Column(name = "device_pin", nullable = false, unique = true, length = 30)
    private String devicePin;

    /** The HaodaAsset Employee.employeeId this PIN belongs to. */
    @Column(name = "employee_id", nullable = false, length = 20)
    private String employeeId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDevicePin() { return devicePin; }
    public void setDevicePin(String devicePin) { this.devicePin = devicePin; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
}
