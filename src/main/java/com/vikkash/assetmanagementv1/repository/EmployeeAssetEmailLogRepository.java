package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.EmployeeAssetEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeAssetEmailLogRepository extends JpaRepository<EmployeeAssetEmailLog, Long> {

    // Most recent first — used by the "Asset Email Logs" page
    List<EmployeeAssetEmailLog> findAllByOrderBySentAtDesc();

    List<EmployeeAssetEmailLog> findByEmployeeIdOrderBySentAtDesc(String employeeId);
}
