package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

public class MobileOtpRequestRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobile;

    @NotBlank(message = "loginAs is required")
    private String loginAs; // "admin" or "employee"

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getLoginAs() { return loginAs; }
    public void setLoginAs(String loginAs) { this.loginAs = loginAs; }
}
