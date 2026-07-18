package com.vikkash.assetmanagementv1.dto;

import com.vikkash.assetmanagementv1.entity.SharedFileAccessLog;
import com.vikkash.assetmanagementv1.entity.SharedFileRecipient;
import com.vikkash.assetmanagementv1.entity.SharedFileVersion;

import java.util.List;

/** Full admin detail view for one shared file: its own stats plus recipients, versions, and the access log. */
public class SharedFileDetailDTO {

    private SharedFileSummaryDTO file;
    private List<SharedFileRecipient> recipients;
    private List<SharedFileVersion> versions;
    private List<SharedFileAccessLog> accessLog;

    public SharedFileDetailDTO() {
    }

    public SharedFileDetailDTO(SharedFileSummaryDTO file, List<SharedFileRecipient> recipients,
                                List<SharedFileVersion> versions, List<SharedFileAccessLog> accessLog) {
        this.file = file;
        this.recipients = recipients;
        this.versions = versions;
        this.accessLog = accessLog;
    }

    public SharedFileSummaryDTO getFile() { return file; }
    public void setFile(SharedFileSummaryDTO file) { this.file = file; }

    public List<SharedFileRecipient> getRecipients() { return recipients; }
    public void setRecipients(List<SharedFileRecipient> recipients) { this.recipients = recipients; }

    public List<SharedFileVersion> getVersions() { return versions; }
    public void setVersions(List<SharedFileVersion> versions) { this.versions = versions; }

    public List<SharedFileAccessLog> getAccessLog() { return accessLog; }
    public void setAccessLog(List<SharedFileAccessLog> accessLog) { this.accessLog = accessLog; }
}
