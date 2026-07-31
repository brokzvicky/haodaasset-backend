package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A physical eSSL/ZKTeco biometric device that talks to this backend over
 * the ADMS (Automatic Data Master Server) push protocol — see
 * {@link com.vikkash.assetmanagementv1.service.AttendanceAdmsService}.
 *
 * Devices self-register the first time they call GET/POST /iclock/cdata
 * with their serial number (SN); nothing needs to be pre-configured here.
 * We keep a row per device so the Attendance Management screen can show a
 * friendly device name and "last seen" timestamp instead of a raw serial
 * number, and so operators can spot a device that has gone offline.
 */
@Entity
@Table(name = "attendance_device", uniqueConstraints = @UniqueConstraint(columnNames = "serial_number"))
public class AttendanceDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The device's unique serial number (SN query param eSSL/ZKTeco devices send on every ADMS call). */
    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    /**
     * Friendly, human-readable name shown in the UI (e.g. "Main Gate Biometric").
     * Defaults to the serial number until an admin renames it via the
     * device mapping screen; there is no device-name field in the ADMS
     * protocol itself.
     */
    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /** Optional site/location label, e.g. "Chennai HQ - Main Gate". */
    @Column(name = "location", length = 150)
    private String location;

    /** IP address the device last called in from — useful for troubleshooting network/firewall issues. */
    @Column(name = "last_ip_address", length = 50)
    private String lastIpAddress;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** Raw firmware/push-version string the device reports on handshake, kept for diagnostics only. */
    @Column(name = "push_version", length = 50)
    private String pushVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLastIpAddress() { return lastIpAddress; }
    public void setLastIpAddress(String lastIpAddress) { this.lastIpAddress = lastIpAddress; }

    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getPushVersion() { return pushVersion; }
    public void setPushVersion(String pushVersion) { this.pushVersion = pushVersion; }
}
