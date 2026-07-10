package com.vikkash.assetmanagementv1.dto;

/** Response for POST /api/admin/asset-email/send and /resend/{logId} — the saved log row plus a friendly message. */
public class SendBulkAssetEmailResponse {

    private EmployeeAssetEmailLogResponse log;
    private String message;

    public SendBulkAssetEmailResponse() {
    }

    public SendBulkAssetEmailResponse(EmployeeAssetEmailLogResponse log, String message) {
        this.log = log;
        this.message = message;
    }

    public EmployeeAssetEmailLogResponse getLog() { return log; }
    public void setLog(EmployeeAssetEmailLogResponse log) { this.log = log; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
