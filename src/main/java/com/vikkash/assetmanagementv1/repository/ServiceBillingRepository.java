package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceBillingRepository extends JpaRepository<ServiceBilling, Long> {

    // ── Dashboard counts ────────────────────────────────────────────────────
    long countByStatus(String status);
    long countByInvoicePathIsNotNull();

    // ── Lookups ─────────────────────────────────────────────────────────────
    List<ServiceBilling> findByStatus(String status);
    List<ServiceBilling> findAllByOrderByPaymentDateDesc();
}
