package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedFileRepository extends JpaRepository<SharedFile, Long> {
    List<SharedFile> findAllByOrderByUploadedAtDesc();
}
