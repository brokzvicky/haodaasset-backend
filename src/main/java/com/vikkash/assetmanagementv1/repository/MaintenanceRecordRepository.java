package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    List<MaintenanceRecord> findByAssetIdOrderByCreatedAtDesc(Long assetId);

    List<MaintenanceRecord> findAllByOrderByCreatedAtDesc();

    List<MaintenanceRecord> findByStatusOrderByScheduledDateAsc(String status);

    long countByStatus(String status);

    void deleteByAssetId(Long assetId);
}
