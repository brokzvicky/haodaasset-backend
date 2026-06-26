package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AssetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRequestRepository extends JpaRepository<AssetRequest, Long> {

    List<AssetRequest> findByEmployeeIdOrderByRequestedAtDesc(String employeeId);
    List<AssetRequest> findAllByOrderByRequestedAtDesc();

    // ── Dashboard counts ───────────────────────────────────────────────────
    long countByStatus(String status);
}
