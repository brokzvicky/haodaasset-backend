package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.SharedFileAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedFileAccessLogRepository extends JpaRepository<SharedFileAccessLog, Long> {
    List<SharedFileAccessLog> findByFileIdOrderByAccessedAtDesc(Long fileId);

    long countByFileIdAndAction(Long fileId, String action);

    SharedFileAccessLog findFirstByFileIdAndActionOrderByAccessedAtDesc(Long fileId, String action);

    void deleteByFileId(Long fileId);
}
