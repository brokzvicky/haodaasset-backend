package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ServiceBillingRepository extends JpaRepository<ServiceBilling, Long> {

    // ── Dashboard counts ────────────────────────────────────────────────────
    long countByStatus(String status);
    long countByInvoicePathIsNotNull();

    // ── Lookups ─────────────────────────────────────────────────────────────
    List<ServiceBilling> findByStatus(String status);
    List<ServiceBilling> findAllByOrderByPaymentDateDesc();

    // ── Recurring billing support ────────────────────────────────────────────

    /** Billing History for one service+vendor combination, latest billing period first. */
    List<ServiceBilling> findByServiceIgnoreCaseAndVendorIgnoreCaseOrderByBillingFromDateDesc(String service, String vendor);

    /** Duplicate-period guard for new records (Requirement 7). */
    boolean existsByServiceIgnoreCaseAndVendorIgnoreCaseAndBillingFromDateAndBillingToDate(
            String service, String vendor, LocalDate billingFromDate, LocalDate billingToDate);

    /** Same check but excludes the record being edited, so editing a record's own period isn't flagged as a clash with itself. */
    boolean existsByServiceIgnoreCaseAndVendorIgnoreCaseAndBillingFromDateAndBillingToDateAndIdNot(
            String service, String vendor, LocalDate billingFromDate, LocalDate billingToDate, Long id);
}
