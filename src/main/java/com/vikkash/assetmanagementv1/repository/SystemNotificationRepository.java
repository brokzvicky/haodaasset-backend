package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.SystemNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, Long> {

    List<SystemNotification> findTop100ByOrderByCreatedAtDesc();

    long countByReadFalse();

    Optional<SystemNotification> findFirstByTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtDesc(
            String type, String relatedType, String relatedId);
}
