package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.AuditLog;
import com.vikkash.assetmanagementv1.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Central "who did what, when" recorder. Other services call record(...) at
 * the point a mutation succeeds; this never throws back into the caller —
 * a failed audit write is logged and swallowed rather than rolling back or
 * failing the real business action that triggered it.
 *
 * REQUIRES_NEW so an audit entry commits independently of the caller's own
 * transaction — useful if a caller's transaction later rolls back for an
 * unrelated reason, though in practice record() is only ever invoked after
 * the primary save has already succeeded.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action, attributing it to whoever is authenticated on the current request thread. */
    public void record(String entityType, String entityId, String action, String description) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String performedBy = "system";
        String performedByRole = null;

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            performedBy = auth.getName();
            performedByRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse(null);
        }

        record(entityType, entityId, action, description, performedBy, performedByRole);
    }

    /** Records an action with an explicitly-known actor (used where the caller already has createdBy/updatedBy on hand). */
    public void record(String entityType, String entityId, String action, String description, String performedBy) {
        record(entityType, entityId, action, description, performedBy, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String entityId, String action, String description,
                        String performedBy, String performedByRole) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setPerformedBy(performedBy);
            entry.setPerformedByRole(performedByRole);
            entry.setDescription(description);
            repository.save(entry);
        } catch (Exception e) {
            // Audit logging must never break the real operation that triggered it.
            log.error("Failed to write audit log entry [{} {} {}]: {}", entityType, action, entityId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecent(String entityType) {
        if (entityType == null || entityType.isBlank() || "All".equalsIgnoreCase(entityType)) {
            return repository.findTop300ByOrderByTimestampDesc();
        }
        return repository.findTop300ByEntityTypeOrderByTimestampDesc(entityType.toUpperCase());
    }
}
