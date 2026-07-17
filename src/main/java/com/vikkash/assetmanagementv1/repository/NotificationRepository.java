package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop300ByStatusNotOrderByCreatedAtDesc(String status);

    List<Notification> findTop300ByOrderByCreatedAtDesc();

    long countByIsReadFalseAndStatusNot(String status);

    List<Notification> findByStatus(String status);

    Optional<Notification> findFirstByNotificationTypeAndRelatedModuleAndRelatedRecordIdOrderByCreatedAtDesc(
            String notificationType, String relatedModule, String relatedRecordId);

    List<Notification> findByStatusAndSnoozedUntilBefore(String status, LocalDateTime now);
}
