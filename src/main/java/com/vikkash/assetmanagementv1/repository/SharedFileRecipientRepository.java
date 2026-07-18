package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.SharedFileRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedFileRecipientRepository extends JpaRepository<SharedFileRecipient, Long> {
    List<SharedFileRecipient> findByFileIdOrderBySharedAtDesc(Long fileId);
    List<SharedFileRecipient> findByEmployeeIdOrderBySharedAtDesc(String employeeId);
    Optional<SharedFileRecipient> findByFileIdAndEmployeeId(Long fileId, String employeeId);

    long countByFileId(Long fileId);
    long countByFileIdAndReadTrue(Long fileId);
    long countByEmployeeIdAndReadFalse(String employeeId);

    void deleteByFileId(Long fileId);
}
