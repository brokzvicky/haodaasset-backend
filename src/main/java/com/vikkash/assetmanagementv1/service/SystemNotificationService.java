package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.SystemNotification;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.SystemNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SystemNotificationService {

    private final SystemNotificationRepository repository;

    public SystemNotificationService(SystemNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SystemNotification> getRecent() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUnreadCount() {
        return Map.of("unread", repository.countByReadFalse());
    }

    @Transactional
    public SystemNotification markRead(Long id) {
        SystemNotification notif = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notif.setRead(true);
        return repository.save(notif);
    }

    @Transactional
    public int markAllRead() {
        List<SystemNotification> unread = repository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(n -> !n.isRead()).toList();
        unread.forEach(n -> n.setRead(true));
        repository.saveAll(unread);
        return unread.size();
    }
}
