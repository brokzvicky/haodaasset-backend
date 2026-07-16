package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Powers the admin Activity Log screen — most recent first, capped so the feed stays fast. */
    List<AuditLog> findTop300ByOrderByTimestampDesc();

    List<AuditLog> findTop300ByEntityTypeOrderByTimestampDesc(String entityType);

    /** Powers the per-asset Timeline view — full history for one asset, oldest first. */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, String entityId);
}
