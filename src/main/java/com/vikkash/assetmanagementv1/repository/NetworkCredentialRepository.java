package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.NetworkCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NetworkCredentialRepository extends JpaRepository<NetworkCredential, Long> {

    // ── Dashboard counts ────────────────────────────────────────────────────
    long countByDeviceType(String deviceType);
    long countByDeviceStatus(String deviceStatus);

    // ── Lookup ──────────────────────────────────────────────────────────────
    List<NetworkCredential> findByDeviceType(String deviceType);
    List<NetworkCredential> findByLocation(String location);

    // ── Recently added / updated (dashboard widgets) ───────────────────────
    List<NetworkCredential> findTop5ByOrderByCreatedAtDesc();
    List<NetworkCredential> findTop5ByOrderByUpdatedAtDesc();

    // ── Global search across the most useful identifying fields ───────────
    @Query("""
            SELECT n FROM NetworkCredential n
            WHERE LOWER(n.deviceName)  LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.deviceType)  LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.brand)       LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.model)       LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.ipAddress)   LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.hostname)    LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.location)    LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(n.username)    LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    List<NetworkCredential> search(@Param("q") String query);
}
