package com.vikkash.assetmanagementv1.dto;

import jakarta.validation.constraints.NotBlank;

public class MobileOtpVerifyRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobile;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "loginAs is required")
    private String loginAs; // "admin" or "employee"

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getLoginAs() { return loginAs; }
    public void setLoginAs(String loginAs) { this.loginAs = loginAs; }
}
