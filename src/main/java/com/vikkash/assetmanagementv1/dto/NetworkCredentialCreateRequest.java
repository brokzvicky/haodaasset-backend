package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public class NetworkCredentialCreateRequest {

    @NotBlank(message = "Device name is required")
    private String deviceName;

    @NotBlank(message = "Device type is required")
    private String deviceType;

    private String brand;
    private String model;

    @Pattern(
        regexp = "^$|^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
        message = "Enter a valid IPv4 address"
    )
    private String ipAddress;

    private String hostname;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    // Optional secondary "enable"/privileged-mode password (common on network gear)
    private String enablePassword;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer sshPort;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer webPort;

    private String location;
    private String vlan;
    private String isp;
    private String notes;
    private String deviceStatus;

    public NetworkCredentialCreateRequest() {}

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

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEnablePassword() { return enablePassword; }
    public void setEnablePassword(String enablePassword) { this.enablePassword = enablePassword; }

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
}
