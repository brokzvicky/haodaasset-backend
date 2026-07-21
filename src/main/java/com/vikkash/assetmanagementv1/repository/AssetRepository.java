package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    // ── Status-based counts used by dashboard ──────────────────────────────
    long countByAssetStatus(String assetStatus);

    // ── Lookup methods ─────────────────────────────────────────────────────
    List<Asset> findByAssetStatus(String assetStatus);
    List<Asset> findByAssetStatusAndAssignmentType(String assetStatus, String assignmentType);
    List<Asset> findByEmployeeName(String employeeName);
    List<Asset> findByEmployeeId(String employeeId);
    Optional<Asset> findBySerialNumber(String serialNumber);

    /**
     * Assets this employee most recently held but are no longer assigned to
     * anyone (i.e. returned, not reassigned onward) — powers the "Returned
     * Assets" list in the Employee Separation module.
     */
    List<Asset> findByLastEmployeeIdAndEmployeeIdIsNull(String lastEmployeeId);

    long countByAssetStatusAndEmployeeId(String assetStatus, String employeeId);

    /** Total assets still assigned to any employee currently in the separation workflow — dashboard "Pending Asset Returns" widget. */
    @Query("SELECT COUNT(a) FROM Asset a WHERE a.assetStatus = 'Assigned' AND a.employeeId IN " +
           "(SELECT e.employeeId FROM Employee e WHERE e.employmentStatus IN ('Notice Period', 'Exit Clearance', 'Assets Returned'))")
    long countPendingAssetReturnsAcrossSeparatingEmployees();

    // ── Serial number uniqueness check (used before saving) ───────────────
    boolean existsBySerialNumber(String serialNumber);

    // ── Distinct value vocab, used by the AI Search intent parser to
    //    validate/typo-correct terms against real live data (never guessed) ──
    @Query("SELECT DISTINCT a.brand FROM Asset a WHERE a.brand IS NOT NULL AND a.brand <> ''")
    List<String> findDistinctBrands();

    @Query("SELECT DISTINCT a.assetType FROM Asset a WHERE a.assetType IS NOT NULL AND a.assetType <> ''")
    List<String> findDistinctAssetTypes();

    @Query("SELECT DISTINCT a.location FROM Asset a WHERE a.location IS NOT NULL AND a.location <> ''")
    List<String> findDistinctLocations();

    @Query("SELECT DISTINCT a.assetStatus FROM Asset a WHERE a.assetStatus IS NOT NULL AND a.assetStatus <> ''")
    List<String> findDistinctStatuses();

    @Query("SELECT DISTINCT a.employeeName FROM Asset a WHERE a.employeeName IS NOT NULL AND a.employeeName <> ''")
    List<String> findDistinctEmployeeNames();
}
