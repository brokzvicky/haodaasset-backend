package com.vikkash.assetmanagementv1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends SMS messages for the "Login with Mobile" OTP flow.
 *
 * IMPORTANT — this default implementation does NOT send a real SMS. No SMS
 * gateway (Twilio, MSG91, AWS SNS, etc.) was configured in this project, so
 * this logs the OTP at INFO level instead of delivering it, purely so the
 * mobile-login flow is functional end-to-end in development.
 *
 * Before using mobile OTP login in production, replace the body of
 * {@link #sendOtp} with a real provider call (e.g. Twilio's Java SDK, or an
 * HTTPS call to MSG91/AWS SNS via the RestTemplate bean already configured
 * in {@code RestTemplateConfig}), following the same pattern EmailService
 * uses for Brevo. Until then, mobile OTP login only works for testers who
 * can read the application log.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    public void sendOtp(String mobile, String otp, long expiryMinutes) {
        log.info("[DEV-ONLY — NO SMS GATEWAY CONFIGURED] OTP for mobile {}: {} (expires in {} min). " +
                        "Wire a real SMS provider into SmsService.sendOtp before production use.",
                maskMobile(mobile), otp, expiryMinutes);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "******" + mobile.substring(mobile.length() - 4);
    }
}
