package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.SharedFileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedFileVersionRepository extends JpaRepository<SharedFileVersion, Long> {
    List<SharedFileVersion> findByFileIdOrderByUploadedAtDesc(Long fileId);
    Optional<SharedFileVersion> findByFileIdAndCurrentTrue(Long fileId);
    void deleteByFileId(Long fileId);
}
