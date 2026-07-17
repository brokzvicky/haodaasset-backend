package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Notification;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Backs the Enterprise Notification Center: creates/reads/updates rows in
 * the unified {@code notifications} table, fans each new one out over
 * Server-Sent Events to any open notification drawers, and — where a
 * recipient is set — sends the matching email via EmailService so the
 * in-app and inbox experiences never drift out of sync.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final EmailService emailService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Value("${app.admin.assignment-notification-email:itsupport@haodapayments.com}")
    private String adminEmail;

    public NotificationService(NotificationRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    // ── Reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getRecent() {
        return repository.findTop300ByStatusNotOrderByCreatedAtDesc("Dismissed");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUnreadCount() {
        return Map.of("unread", repository.countByIsReadFalseAndStatusNot("Dismissed"));
    }

    // ── Writes: mark read / snooze / complete / clear ───────────────────

    @Transactional
    public Notification markRead(Long id) {
        Notification n = get(id);
        n.setRead(true);
        return repository.save(n);
    }

    @Transactional
    public int markAllRead() {
        List<Notification> unread = repository.findTop300ByStatusNotOrderByCreatedAtDesc("Dismissed").stream()
                .filter(n -> !n.isRead()).toList();
        unread.forEach(n -> n.setRead(true));
        repository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public Notification snooze(Long id, int minutes) {
        Notification n = get(id);
        n.setStatus("Snoozed");
        n.setSnoozedUntil(LocalDateTime.now().plusMinutes(minutes));
        n.setRead(true);
        return repository.save(n);
    }

    @Transactional
    public Notification markActioned(Long id) {
        Notification n = get(id);
        n.setStatus("Actioned");
        n.setRead(true);
        return repository.save(n);
    }

    @Transactional
    public int clearCompleted() {
        List<Notification> actioned = repository.findByStatus("Actioned");
        actioned.forEach(n -> n.setStatus("Dismissed"));
        repository.saveAll(actioned);
        return actioned.size();
    }

    private Notification get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
    }

    // ── Dedup helper used by all reminder scanners ──────────────────────

    /** True if a notification of this type+related-record was already created today. */
    @Transactional(readOnly = true)
    public boolean alreadyCreatedToday(String notificationType, String relatedModule, String relatedRecordId) {
        Optional<Notification> existing = repository
                .findFirstByNotificationTypeAndRelatedModuleAndRelatedRecordIdOrderByCreatedAtDesc(
                        notificationType, relatedModule, relatedRecordId);
        return existing.map(n -> n.getCreatedAt().toLocalDate().isEqual(LocalDateTime.now().toLocalDate())).orElse(false);
    }

    // ── Core create — persists, broadcasts over SSE, and emails ─────────

    public static class Recipients {
        public boolean toAdmin = true;
        public String assigneeEmail;
    }

    @Transactional
    public Notification create(String notificationType, String category, String priority, String title,
                                String description, String relatedModule, String relatedRecordId,
                                Recipients recipients) {
        return create(notificationType, category, priority, title, description, relatedModule, relatedRecordId, null, recipients);
    }

    @Transactional
    public Notification create(String notificationType, String category, String priority, String title,
                                String description, String relatedModule, String relatedRecordId,
                                java.time.LocalDate dueDate, Recipients recipients) {
        Notification n = new Notification();
        n.setNotificationType(notificationType);
        n.setCategory(category);
        n.setPriority(priority != null ? priority : "Normal");
        n.setTitle(title);
        n.setDescription(description);
        n.setRelatedModule(relatedModule);
        n.setRelatedRecordId(relatedRecordId);
        n.setDueDate(dueDate);
        n.setStatus("Pending");
        n.setScheduledAt(LocalDateTime.now());
        n.setRecipient(recipients != null && recipients.assigneeEmail != null ? recipients.assigneeEmail : "ADMIN");
        n = repository.save(n);

        broadcast(n);
        dispatchEmails(n, recipients);
        return n;
    }

    private void dispatchEmails(Notification n, Recipients recipients) {
        boolean toAdmin = recipients == null || recipients.toAdmin;
        String assigneeEmail = recipients != null ? recipients.assigneeEmail : null;

        if (toAdmin && adminEmail != null && !adminEmail.isBlank()) {
            try {
                emailService.sendSimpleNotificationEmail(adminEmail, n.getTitle(),
                        prettyType(n.getNotificationType()), buildEmailHtml(n));
                n.setEmailSentAdmin(true);
            } catch (Exception ex) {
                log.warn("Failed to email admin for notification {}: {}", n.getNotificationId(), ex.getMessage());
            }
        }
        if (assigneeEmail != null && !assigneeEmail.isBlank()) {
            try {
                emailService.sendSimpleNotificationEmail(assigneeEmail, n.getTitle(),
                        prettyType(n.getNotificationType()), buildEmailHtml(n));
                n.setEmailSentAssignee(true);
            } catch (Exception ex) {
                log.warn("Failed to email assignee for notification {}: {}", n.getNotificationId(), ex.getMessage());
            }
        }
        n.setSentAt(LocalDateTime.now());
        n.setStatus("Sent");
        repository.save(n);
    }

    private String buildEmailHtml(Notification n) {
        return "<p><b>" + n.getTitle() + "</b></p><p>" + (n.getDescription() != null ? n.getDescription() : "") + "</p>";
    }

    private String prettyType(String type) {
        if (type == null) return "Notification";
        String[] parts = type.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ── Real-time (SSE) ──────────────────────────────────────────────────

    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — client reconnects on drop
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (Exception ignored) { /* client disconnected immediately */ }
        return emitter;
    }

    private void broadcast(Notification n) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(n));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }
}
