package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.NetworkCredential;

import java.time.LocalDateTime;

/**
 * What the frontend receives for list/get/search/create/update calls.
 *
 * Deliberately has NO password field at all (not even encrypted/masked) —
 * the table renders a static "Hidden" placeholder client-side and only
 * calls the separate reveal endpoint when an admin explicitly clicks
 * "Show Password" for one specific row. This keeps password ciphertext
 * out of the browser entirely until that exact moment, and out of browser
 * history / dev-tools network logs for every other request.
 */
public class NetworkCredentialResponse {

    private Long id;
    private String deviceName;
    private String deviceType;
    private String brand;
    private String model;
    private String ipAddress;
    private String hostname;
    private String username;
    private boolean hasEnablePassword;
    private Integer sshPort;
    private Integer webPort;
    private String location;
    private String vlan;
    private String isp;
    private String notes;
    private String deviceStatus;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NetworkCredentialResponse from(NetworkCredential c) {
        NetworkCredentialResponse r = new NetworkCredentialResponse();
        r.id = c.getId();
        r.deviceName = c.getDeviceName();
        r.deviceType = c.getDeviceType();
        r.brand = c.getBrand();
        r.model = c.getModel();
        r.ipAddress = c.getIpAddress();
        r.hostname = c.getHostname();
        r.username = c.getUsername();
        r.hasEnablePassword = c.getEncryptedEnablePassword() != null && !c.getEncryptedEnablePassword().isBlank();
        r.sshPort = c.getSshPort();
        r.webPort = c.getWebPort();
        r.location = c.getLocation();
        r.vlan = c.getVlan();
        r.isp = c.getIsp();
        r.notes = c.getNotes();
        r.deviceStatus = c.getDeviceStatus();
        r.createdBy = c.getCreatedBy();
        r.updatedBy = c.getUpdatedBy();
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();
        r.rotationDueDate = c.getRotationDueDate();
        r.firmwareDueDate = c.getFirmwareDueDate();
        return r;
    }

    private java.time.LocalDate rotationDueDate;
    private java.time.LocalDate firmwareDueDate;

    public java.time.LocalDate getRotationDueDate() { return rotationDueDate; }
    public void setRotationDueDate(java.time.LocalDate rotationDueDate) { this.rotationDueDate = rotationDueDate; }

    public java.time.LocalDate getFirmwareDueDate() { return firmwareDueDate; }
    public void setFirmwareDueDate(java.time.LocalDate firmwareDueDate) { this.firmwareDueDate = firmwareDueDate; }

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

    public boolean isHasEnablePassword() { return hasEnablePassword; }
    public void setHasEnablePassword(boolean hasEnablePassword) { this.hasEnablePassword = hasEnablePassword; }

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
