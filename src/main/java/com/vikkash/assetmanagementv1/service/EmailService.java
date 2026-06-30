package com.vikkash.assetmanagementv1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vikkash.assetmanagementv1.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Sends transactional security emails (OTP codes) via the Brevo
 * Transactional Email REST API (HTTPS, port 443) instead of SMTP.
 * SMTP is intentionally not used here because outbound SMTP ports
 * (25/465/587) are blocked on Render's network, which previously
 * caused SocketTimeoutException failures.
 *
 * Reused by both the Admin Forgot Password flow and the Network
 * Credential unlock flow.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.brevo.api-key}")
    private String brevoApiKey;

    public EmailService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a styled OTP email.
     *
     * @param to            recipient address
     * @param heading       short context, e.g. "Admin Password Reset" or "Network Credential Access"
     * @param otp           the 6-digit code
     * @param expiryMinutes how long the code is valid for
     */
    public void sendOtpEmail(String to, String heading, String otp, long expiryMinutes) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            ObjectNode sender = root.putObject("sender");
            sender.put("name", fromName);
            sender.put("email", fromAddress);

            ObjectNode recipient = objectMapper.createObjectNode();
            recipient.put("email", to);
            root.putArray("to").add(recipient);

            root.put("subject", heading + " — Your AssetTower verification code");
            root.put("htmlContent", buildHtml(heading, otp, expiryMinutes));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);

            restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("OTP email sent via Brevo API: heading={} to={}", heading, maskEmail(to));
        } catch (ResourceAccessException ex) {
            log.error("Network error calling Brevo API for {}: {}", maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the verification email right now. Please try again in a moment.", ex);
        } catch (RestClientException ex) {
            HttpStatusCode status = extractStatus(ex);
            log.error("Brevo API rejected OTP email to {} (status={}): {}", maskEmail(to), status, ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the verification email right now. Please try again in a moment.", ex);
        } catch (Exception ex) {
            log.error("Unexpected failure sending OTP email to {}: {}", maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the verification email right now. Please try again in a moment.", ex);
        }
    }

    private HttpStatusCode extractStatus(RestClientException ex) {
        if (ex instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode();
        }
        return null;
    }

    private String buildHtml(String heading, String otp, long expiryMinutes) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 0;">
                    <tr><td align="center">
                      <table width="460" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="background:linear-gradient(135deg,#1d4ed8,#3b82f6);padding:28px 32px;">
                            <div style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:0.3px;">AssetTower</div>
                            <div style="color:#dbeafe;font-size:12.5px;margin-top:2px;">Enterprise IT Asset Management</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <div style="font-size:16px;font-weight:700;color:#0f172a;margin-bottom:6px;">%s</div>
                            <p style="font-size:13.5px;color:#475569;line-height:1.6;margin:0 0 22px;">
                              Use the verification code below to continue. This code is valid for
                              <strong>%d minutes</strong> and can only be used once.
                            </p>
                            <div style="background:#f0f7ff;border:1.5px dashed #93c5fd;border-radius:10px;padding:18px;text-align:center;margin-bottom:22px;">
                              <span style="font-size:32px;font-weight:800;letter-spacing:10px;color:#1d4ed8;">%s</span>
                            </div>
                            <p style="font-size:12.5px;color:#64748b;line-height:1.6;margin:0 0 4px;">
                              If you didn't request this, you can safely ignore this email — no changes
                              will be made to your account.
                            </p>
                            <p style="font-size:12.5px;color:#94a3b8;line-height:1.6;margin:0;">
                              Never share this code with anyone, including AssetTower staff.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                            <div style="font-size:11px;color:#94a3b8;">
                              This is an automated security message from AssetTower. Please do not reply.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, expiryMinutes, otp);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
