package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * Represents a network/infrastructure device's access credentials
 * (routers, switches, firewalls, access points, servers, NAS, printers,
 * VPN gateways, etc).
 *
 * Security note: `encryptedPassword` and `encryptedEnablePassword` never
 * hold plaintext — they are always AES-encrypted ciphertext (Base64) by
 * NetworkCredentialService before being persisted, and are only decrypted
 * on an explicit, audited "reveal" request. They must never be exposed via
 * the standard list/get responses — see NetworkCredentialResponse, which
 * deliberately omits them.
 *
 * IMPORTANT: These String fields are intentionally NOT annotated with
 * @Lob. On Hibernate 6 (Spring Boot 3), @Lob on a String routes through a
 * streamed CLOB binding instead of a plain varchar/text binding, which can
 * fail to round-trip byte-exact data through PostgreSQL via HikariCP —
 * corrupting Base64 ciphertext silently on write and breaking AES-GCM
 * decryption on every read. Plain `text` columns (no @Lob) round-trip
 * correctly and have no practical length limit in PostgreSQL, so @Lob is
 * unnecessary here.
 */
@Entity
@Table(
        name = "network_credentials",
        indexes = {
                @Index(name = "idx_netcred_device_type", columnList = "deviceType"),
                @Index(name = "idx_netcred_location",    columnList = "location"),
                @Index(name = "idx_netcred_ip_address",  columnList = "ipAddress")
        }
)
public class NetworkCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Device name is required")
    private String deviceName;

    @NotBlank(message = "Device type is required")
    private String deviceType;

    private String brand;
    private String model;

    private String ipAddress;
    private String hostname;

    @NotBlank(message = "Username is required")
    private String username;

    // AES ciphertext (Base64), never plaintext. See class-level note.
    // No @Lob — see class-level note for why.
    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "encrypted_enable_password", columnDefinition = "TEXT")
    private String encryptedEnablePassword;

    private Integer sshPort;
    private Integer webPort;

    private String location;
    private String vlan;
    private String isp;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "device_status")
    private String deviceStatus = "Active";

    // ── Audit fields ────────────────────────────────────────────────────────
    private String createdBy;
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public NetworkCredential() {}

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.deviceStatus == null || this.deviceStatus.isBlank()) {
            this.deviceStatus = "Active";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public String getEncryptedEnablePassword() { return encryptedEnablePassword; }
    public void setEncryptedEnablePassword(String encryptedEnablePassword) { this.encryptedEnablePassword = encryptedEnablePassword; }

    public Integer getSshPort() { return sshPort; }
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }

    public Integer getWebPort() { return webPort; }
    public void setWebPort(Integer webPort) { this.webPort = webPort; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getVlan() { return vlan; }
    public void setVlan(String vlan) { this.vlan = vlan; }

    public String getIsp() { return isp; }
    public void setIsp(String isp) { this.isp = isp; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(String deviceStatus) { this.deviceStatus = deviceStatus; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}