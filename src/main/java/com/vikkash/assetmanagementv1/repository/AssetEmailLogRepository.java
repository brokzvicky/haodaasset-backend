package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AssetEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetEmailLogRepository extends JpaRepository<AssetEmailLog, Long> {

    // Most recent first — used by the Email Logs page
    List<AssetEmailLog> findAllByOrderBySentAtDesc();

    List<AssetEmailLog> findByAssetIdOrderBySentAtDesc(Long assetId);
}
