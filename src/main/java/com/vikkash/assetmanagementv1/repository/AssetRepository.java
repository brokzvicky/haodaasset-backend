package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    // ── Status-based counts used by dashboard ──────────────────────────────
    long countByAssetStatus(String assetStatus);

    // ── Lookup methods ─────────────────────────────────────────────────────
    List<Asset> findByAssetStatus(String assetStatus);
    List<Asset> findByEmployeeName(String employeeName);
    List<Asset> findByEmployeeId(String employeeId);
    Optional<Asset> findBySerialNumber(String serialNumber);

    // ── Serial number uniqueness check (used before saving) ───────────────
    boolean existsBySerialNumber(String serialNumber);

    /**
     * Used during assignment to verify the asset is currently Available
     * without loading the full entity just for a status check.
     */
    @Query("SELECT a.assetStatus FROM Asset a WHERE a.assetId = :assetId")
    Optional<String> findStatusById(Long assetId);
}
