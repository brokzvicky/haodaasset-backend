package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AssetDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetDocumentRepository extends JpaRepository<AssetDocument, Long> {

    List<AssetDocument> findByAssetIdOrderByUploadedAtDesc(Long assetId);

    long countByAssetId(Long assetId);

    void deleteByAssetId(Long assetId);
}
