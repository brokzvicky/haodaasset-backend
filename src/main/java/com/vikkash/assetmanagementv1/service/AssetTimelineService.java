package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.TimelineEventDTO;
import com.vikkash.assetmanagementv1.entity.AssetDocument;
import com.vikkash.assetmanagementv1.entity.AssetEmailLog;
import com.vikkash.assetmanagementv1.entity.AuditLog;
import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import com.vikkash.assetmanagementv1.repository.AssetDocumentRepository;
import com.vikkash.assetmanagementv1.repository.AssetEmailLogRepository;
import com.vikkash.assetmanagementv1.repository.AuditLogRepository;
import com.vikkash.assetmanagementv1.repository.MaintenanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Powers the Asset Timeline view: a single chronological feed of everything
 * that has ever happened to one asset — audit trail entries (created,
 * assigned, returned, ...), assignment emails sent, maintenance events, and
 * document uploads — merged and sorted oldest to newest.
 */
@Service
public class AssetTimelineService {

    private final AuditLogRepository auditLogRepository;
    private final AssetEmailLogRepository emailLogRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final AssetDocumentRepository documentRepository;

    public AssetTimelineService(AuditLogRepository auditLogRepository,
                                 AssetEmailLogRepository emailLogRepository,
                                 MaintenanceRecordRepository maintenanceRepository,
                                 AssetDocumentRepository documentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.emailLogRepository = emailLogRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDTO> getTimeline(Long assetId) {
        List<TimelineEventDTO> events = new ArrayList<>();
        String assetIdStr = String.valueOf(assetId);

        for (AuditLog log : auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc("ASSET", assetIdStr)) {
            events.add(new TimelineEventDTO("AUDIT", log.getAction(), log.getDescription(),
                    log.getPerformedBy(), log.getTimestamp()));
        }

        for (AssetEmailLog email : emailLogRepository.findByAssetIdOrderBySentAtDesc(assetId)) {
            events.add(new TimelineEventDTO("EMAIL", email.getEmailType(),
                    email.getEmailType() + " email " + (email.getStatus() != null ? email.getStatus() : "sent")
                            + " to " + email.getEmployeeEmail(),
                    email.getSentByAdmin(),
                    LocalDateTime.ofInstant(email.getSentAt(), ZoneId.systemDefault())));
        }

        for (MaintenanceRecord m : maintenanceRepository.findByAssetIdOrderByCreatedAtDesc(assetId)) {
            events.add(new TimelineEventDTO("MAINTENANCE", m.getStatus(),
                    m.getMaintenanceType() + " maintenance — " + m.getStatus()
                            + (m.getDescription() != null ? ": " + m.getDescription() : ""),
                    m.getCreatedBy(), m.getCreatedAt()));
        }

        for (AssetDocument d : documentRepository.findByAssetIdOrderByUploadedAtDesc(assetId)) {
            events.add(new TimelineEventDTO("DOCUMENT", "UPLOADED",
                    "Document uploaded: " + d.getOriginalFileName() + " (" + d.getDocumentType() + ")",
                    d.getUploadedBy(), d.getUploadedAt()));
        }

        events.sort(Comparator.comparing(TimelineEventDTO::getTimestamp));
        return events;
    }
}
