package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.AuditLog;
import com.vikkash.assetmanagementv1.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Central "who did what, when" recorder. Other services call record(...) at
 * the point a mutation succeeds; this never throws back into the caller —
 * a failed audit write is logged and swallowed rather than rolling back or
 * failing the real business action that triggered it.
 *
 * IMPORTANT — why this uses TransactionTemplate instead of a declarative
 * @Transactional(REQUIRES_NEW) method with an internal try/catch:
 *
 * A declarative @Transactional method that catches its own exception looks
 * safe, but isn't. If repository.save() fails (e.g. a constraint violation),
 * Hibernate marks the current persistence context rollback-only the moment
 * the failure happens — independent of whether the Java exception is caught.
 * The method then returns normally, so Spring's AOP advice sees no
 * exception and tries to COMMIT the transaction it opened — discovers it's
 * flagged rollback-only, and throws UnexpectedRollbackException from the
 * proxy itself, AFTER the try/catch has already returned. That exception
 * propagates straight into the caller (e.g. NetworkCredentialService.
 * revealPassword()) as an unhandled, unrelated-looking 500 — exactly the
 * "reveal-password randomly 500s" bug this class previously caused.
 *
 * TransactionTemplate avoids this: if the callback throws, it triggers a
 * clean ROLLBACK (never attempts a commit) and rethrows the original
 * exception to the caller of execute(...) — which is here, OUTSIDE any
 * transactional AOP boundary, where try/catch works exactly as intended.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AuditLogService(AuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransactionTemplate.setName("audit-log-write");
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

    /**
     * Writes one audit entry in its own, isolated transaction. Never throws —
     * any failure (constraint violation, connection issue, etc.) is logged
     * with full detail and swallowed here, safely outside the transactional
     * boundary, so it can never surface as an unrelated 500 in the caller.
     */
    public void record(String entityType, String entityId, String action, String description,
                        String performedBy, String performedByRole) {
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> {
                AuditLog entry = new AuditLog();
                entry.setEntityType(entityType);
                entry.setEntityId(entityId);
                entry.setAction(action);
                entry.setPerformedBy(performedBy);
                entry.setPerformedByRole(performedByRole);
                entry.setDescription(description);
                repository.save(entry);
                repository.flush(); // surface any constraint violation HERE, inside this try, not later at an implicit commit
            });
        } catch (Exception e) {
            // Audit logging must never break the real operation that triggered it.
            // Full stack trace logged so a persistently-failing audit write (e.g. a
            // NOT NULL / column-length constraint mismatch) is actually diagnosable,
            // instead of silently dropping every entry for this call site forever.
            log.error("Failed to write audit log entry [{} {} {}] performedBy={}: {}",
                    entityType, action, entityId, performedBy, e.getMessage(), e);
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
