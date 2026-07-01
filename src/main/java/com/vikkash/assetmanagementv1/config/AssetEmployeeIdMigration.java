package com.vikkash.assetmanagementv1.config;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time idempotent migration that runs at every startup.
 *
 * Root cause it fixes:
 *   When an asset is assigned, AssetService used to store the raw employeeId
 *   from the HTTP request (e.g. "EMP006", "emp006", "EMP006 ") without
 *   normalising it. EmployeeService.getAssetsForEmployee() always queries with
 *   .trim().toUpperCase(), so any mismatch in case or whitespace caused
 *   findByEmployeeId() to return an empty list — making the employee's panel
 *   show "No assets currently assigned" even though the asset row said
 *   "Assigned → Bhargav Perubona".
 *
 * Fix applied to new assignments: AssetService.assignAsset() now normalises
 *   on write. This migration back-fills existing rows in the database so that
 *   historically-assigned assets are also corrected.
 *
 * It is safe to run repeatedly: if the value is already upper-case and trimmed
 * it is left untouched and no UPDATE is issued.
 */
@Component
public class AssetEmployeeIdMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssetEmployeeIdMigration.class);

    private final AssetRepository assetRepository;

    public AssetEmployeeIdMigration(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Asset> all = assetRepository.findAll();
        int fixed = 0;
        for (Asset asset : all) {
            String raw = asset.getEmployeeId();
            if (raw == null || raw.isBlank()) continue;
            String normalised = raw.trim().toUpperCase();
            if (!normalised.equals(raw)) {
                asset.setEmployeeId(normalised);
                assetRepository.save(asset);
                fixed++;
                log.info("Normalised employeeId on asset id={}: '{}' → '{}'",
                        asset.getAssetId(), raw, normalised);
            }
        }
        if (fixed > 0) {
            log.info("AssetEmployeeIdMigration: normalised {} asset record(s).", fixed);
        } else {
            log.info("AssetEmployeeIdMigration: all records already normalised, nothing to do.");
        }
    }
}
