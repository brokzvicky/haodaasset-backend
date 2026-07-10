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

import java.util.List;

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

    /**
     * Sends the "Asset Assignment" notification email to an employee,
     * confirming which asset was assigned to them and when.
     *
     * @param to           employee's email address
     * @param employeeName employee's display name
     * @param employeeId   employee's business ID, e.g. EMP002
     * @param assetDetails asset fields to render into the email body
     */
    public void sendAssetAssignmentEmail(String to, String employeeName, String employeeId,
                                          AssetAssignmentEmailDetails assetDetails) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            ObjectNode sender = root.putObject("sender");
            sender.put("name", fromName);
            sender.put("email", fromAddress);

            ObjectNode recipient = objectMapper.createObjectNode();
            recipient.put("email", to);
            recipient.put("name", employeeName);
            root.putArray("to").add(recipient);

            root.put("subject", "Asset Assigned To You — " + assetDetails.assetName());
            root.put("htmlContent", buildAssetAssignmentHtml(employeeName, employeeId, assetDetails));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);

            restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("Asset assignment email sent via Brevo API: asset={} to={}",
                    assetDetails.assetName(), maskEmail(to));
        } catch (ResourceAccessException ex) {
            log.error("Network error calling Brevo API for asset assignment email to {}: {}",
                    maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the assignment email right now. Please try again in a moment.", ex);
        } catch (RestClientException ex) {
            HttpStatusCode status = extractStatus(ex);
            log.error("Brevo API rejected asset assignment email to {} (status={}): {}",
                    maskEmail(to), status, ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the assignment email right now. Please try again in a moment.", ex);
        } catch (Exception ex) {
            log.error("Unexpected failure sending asset assignment email to {}: {}", maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the assignment email right now. Please try again in a moment.", ex);
        }
    }

    /** Immutable bag of asset fields needed to render the assignment email. */
    public record AssetAssignmentEmailDetails(
            Long assetId,
            String assetName,
            String brand,
            String model,
            String serialNumber,
            String assignedDate,
            String location
    ) {}

    /** Immutable bag of asset fields needed to render one row of the bulk "Send Asset Email" table. */
    public record BulkAssetRow(
            Long assetId,
            String assetType,
            String assetName,
            String brand,
            String model,
            String serialNumber,
            String location,
            String assignedDate
    ) {}

    /** Employee fields needed to render the bulk "Send Asset Email" details card. */
    public record BulkEmailEmployeeDetails(
            String employeeName,
            String employeeId,
            String department,
            String designation,
            String location
    ) {}

    /**
     * Sends the "Send Asset Email" notification for the enterprise bulk-send
     * admin page: one email listing every asset the admin selected for this
     * employee, rather than the single-asset assignment email above.
     *
     * @param to        employee's email address
     * @param employee  employee fields for the details card
     * @param assets    every asset row to include in the email table
     */
    public void sendBulkAssetAssignmentEmail(String to, BulkEmailEmployeeDetails employee, List<BulkAssetRow> assets) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            ObjectNode sender = root.putObject("sender");
            sender.put("name", fromName);
            sender.put("email", fromAddress);

            ObjectNode recipient = objectMapper.createObjectNode();
            recipient.put("email", to);
            recipient.put("name", employee.employeeName());
            root.putArray("to").add(recipient);

            String subject = assets.size() == 1
                    ? "Your Assigned IT Asset — " + nullSafe(assets.get(0).assetName())
                    : "Your Assigned IT Assets (" + assets.size() + ") — Haoda Asset";
            root.put("subject", subject);
            root.put("htmlContent", buildBulkAssetEmailHtml(employee, assets));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);

            restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("Bulk asset email sent via Brevo API: employee={} assetCount={} to={}",
                    employee.employeeId(), assets.size(), maskEmail(to));
        } catch (ResourceAccessException ex) {
            log.error("Network error calling Brevo API for bulk asset email to {}: {}", maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the asset email right now. Please try again in a moment.", ex);
        } catch (RestClientException ex) {
            HttpStatusCode status = extractStatus(ex);
            log.error("Brevo API rejected bulk asset email to {} (status={}): {}", maskEmail(to), status, ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the asset email right now. Please try again in a moment.", ex);
        } catch (Exception ex) {
            log.error("Unexpected failure sending bulk asset email to {}: {}", maskEmail(to), ex.getMessage());
            throw new EmailDeliveryException(
                    "Couldn't send the asset email right now. Please try again in a moment.", ex);
        }
    }

    private String buildBulkAssetEmailHtml(BulkEmailEmployeeDetails employee, List<BulkAssetRow> assets) {
        StringBuilder rows = new StringBuilder();
        for (BulkAssetRow a : assets) {
            rows.append("""
                    <tr>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">#%d</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;font-weight:600;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:12px;color:#334155;">%s</td>
                    </tr>
                    """.formatted(
                    a.assetId(), nullSafe(a.assetType()), nullSafe(a.assetName()), nullSafe(a.brand()),
                    nullSafe(a.model()), nullSafe(a.serialNumber()), nullSafe(a.assignedDate())
            ));
        }

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 0;">
                    <tr><td align="center">
                      <table width="640" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="background:linear-gradient(135deg,#1d4ed8,#3b82f6);padding:28px 32px;">
                            <table cellpadding="0" cellspacing="0"><tr>
                              <td style="width:38px;height:38px;background:#ffffff;border-radius:9px;text-align:center;vertical-align:middle;font-weight:800;color:#1d4ed8;font-size:15px;">H</td>
                              <td style="padding-left:12px;">
                                <div style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:0.3px;">Haoda</div>
                                <div style="color:#dbeafe;font-size:12.5px;margin-top:1px;">Enterprise IT Asset Management</div>
                              </td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <div style="font-size:16px;font-weight:700;color:#0f172a;margin-bottom:6px;">Hi %s,</div>
                            <p style="font-size:13.5px;color:#475569;line-height:1.6;margin:0 0 22px;">
                              This is a summary of the IT asset%s currently assigned to you. Please take care of
                              %s and reach out to IT Support if you notice any issue.
                            </p>

                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;margin-bottom:20px;">
                              <tr><td style="padding:14px 18px;">
                                <div style="font-size:12px;font-weight:700;color:#0f172a;margin-bottom:6px;">Employee Details</div>
                                <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:12.5px;color:#334155;">
                                  <tr><td style="padding:2px 0;color:#64748b;width:130px;">Name</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:2px 0;color:#64748b;">Employee ID</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:2px 0;color:#64748b;">Department</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:2px 0;color:#64748b;">Designation</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:2px 0;color:#64748b;">Location</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                </table>
                              </td></tr>
                            </table>

                            <div style="font-size:13px;font-weight:700;color:#0f172a;margin-bottom:10px;">
                              Assigned Assets (%d)
                            </div>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;">
                              <thead>
                                <tr style="background:#f0f7ff;">
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Asset ID</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Type</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Asset</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Brand</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Model</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Serial No.</th>
                                  <th align="left" style="padding:10px 12px;font-size:11px;color:#1d4ed8;text-transform:uppercase;letter-spacing:0.04em;">Assigned</th>
                                </tr>
                              </thead>
                              <tbody>
                                %s
                              </tbody>
                            </table>

                            <p style="font-size:12.5px;color:#64748b;line-height:1.6;margin:22px 0 4px;">
                              Questions about any of these assets? Contact <strong>IT Support</strong> at
                              <a href="mailto:it-support@haodapayments.com" style="color:#1d4ed8;text-decoration:none;">it-support@haodapayments.com</a>.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                            <div style="font-size:11px;color:#94a3b8;">
                              This is an automated message from Haoda Asset. Please do not reply directly to this email.
                            </div>
                            <div style="font-size:11px;color:#cbd5e1;margin-top:4px;">
                              © %d Haoda Payments. All rights reserved.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                        employee.employeeName(),
                        assets.size() == 1 ? "" : "s",
                        assets.size() == 1 ? "it" : "them",
                        employee.employeeName(), employee.employeeId(),
                        nullSafe(employee.department()), nullSafe(employee.designation()), nullSafe(employee.location()),
                        assets.size(),
                        rows.toString(),
                        java.time.Year.now().getValue()
                );
    }

    private String buildAssetAssignmentHtml(String employeeName, String employeeId,
                                             AssetAssignmentEmailDetails a) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 0;">
                    <tr><td align="center">
                      <table width="520" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                        <tr>
                          <td style="background:linear-gradient(135deg,#1d4ed8,#3b82f6);padding:28px 32px;">
                            <table cellpadding="0" cellspacing="0"><tr>
                              <td style="width:38px;height:38px;background:#ffffff;border-radius:9px;text-align:center;vertical-align:middle;font-weight:800;color:#1d4ed8;font-size:15px;">H</td>
                              <td style="padding-left:12px;">
                                <div style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:0.3px;">Haoda</div>
                                <div style="color:#dbeafe;font-size:12.5px;margin-top:1px;">Enterprise IT Asset Management</div>
                              </td>
                            </tr></table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <div style="font-size:16px;font-weight:700;color:#0f172a;margin-bottom:6px;">Hi %s,</div>
                            <p style="font-size:13.5px;color:#475569;line-height:1.6;margin:0 0 22px;">
                              The following asset has been assigned to you. Please take care of it and
                              reach out to IT Support if you notice any issue.
                            </p>

                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f7ff;border:1px solid #bfdbfe;border-radius:10px;margin-bottom:18px;">
                              <tr><td style="padding:16px 18px;">
                                <div style="font-size:15px;font-weight:700;color:#1d4ed8;margin-bottom:10px;">%s</div>
                                <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:12.5px;color:#334155;">
                                  <tr><td style="padding:3px 0;color:#64748b;width:120px;">Asset ID</td><td style="padding:3px 0;font-weight:600;">#%d</td></tr>
                                  <tr><td style="padding:3px 0;color:#64748b;">Brand</td><td style="padding:3px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:3px 0;color:#64748b;">Model</td><td style="padding:3px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:3px 0;color:#64748b;">Serial Number</td><td style="padding:3px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:3px 0;color:#64748b;">Assigned Date</td><td style="padding:3px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:3px 0;color:#64748b;">Location</td><td style="padding:3px 0;font-weight:600;">%s</td></tr>
                                </table>
                              </td></tr>
                            </table>

                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;margin-bottom:6px;">
                              <tr><td style="padding:14px 18px;">
                                <div style="font-size:12px;font-weight:700;color:#0f172a;margin-bottom:6px;">Assigned To</div>
                                <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:12.5px;color:#334155;">
                                  <tr><td style="padding:2px 0;color:#64748b;width:120px;">Name</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                  <tr><td style="padding:2px 0;color:#64748b;">Employee ID</td><td style="padding:2px 0;font-weight:600;">%s</td></tr>
                                </table>
                              </td></tr>
                            </table>

                            <p style="font-size:12.5px;color:#64748b;line-height:1.6;margin:20px 0 4px;">
                              Questions about this asset? Contact <strong>IT Support</strong> at
                              <a href="mailto:it-support@haodapayments.com" style="color:#1d4ed8;text-decoration:none;">it-support@haodapayments.com</a>.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                            <div style="font-size:11px;color:#94a3b8;">
                              This is an automated message from Haoda Asset. Please do not reply directly to this email.
                            </div>
                            <div style="font-size:11px;color:#cbd5e1;margin-top:4px;">
                              © %d Haoda Payments. All rights reserved.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                        employeeName,
                        a.assetName(), a.assetId(), nullSafe(a.brand()), nullSafe(a.model()),
                        nullSafe(a.serialNumber()), nullSafe(a.assignedDate()), nullSafe(a.location()),
                        employeeName, employeeId,
                        java.time.Year.now().getValue()
                );
    }

    private String nullSafe(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
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
