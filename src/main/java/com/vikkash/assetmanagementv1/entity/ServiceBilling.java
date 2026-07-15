package com.vikkash.assetmanagementv1.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single service/vendor payment tracked under the
 * "Service Billing" module (e.g. AMC renewals, internet/ISP bills,
 * software subscriptions, maintenance contracts, etc).
 *
 * invoicePath stores only the relative path to the uploaded PDF invoice on
 * disk (see ServiceBillingService for storage details) — the file itself
 * never lives in the database.
 */
@Entity
@Table(
    name = "service_billing",
    indexes = {
        @Index(name = "idx_service_billing_status",       columnList = "status"),
        @Index(name = "idx_service_billing_payment_date",  columnList = "paymentDate"),
        @Index(name = "idx_service_billing_vendor",        columnList = "vendor")
    }
)
public class ServiceBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Service is required")
    private String service;

    @NotBlank(message = "Vendor is required")
    private String vendor;

    @NotNull(message = "Amount is required")
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /** One of: Paid, Pending, Overdue. */
    @NotBlank(message = "Status is required")
    private String status = "Pending";

    /** Relative path (under the configured upload root) to the stored PDF invoice, if any. */
    @Column(name = "invoice_path")
    private String invoicePath;

    /** Original filename of the uploaded invoice, kept for a friendlier download name. */
    @Column(name = "invoice_original_name")
    private String invoiceOriginalName;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ServiceBilling() {}

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null || this.status.isBlank()) {
            this.status = "Pending";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInvoicePath() { return invoicePath; }
    public void setInvoicePath(String invoicePath) { this.invoicePath = invoicePath; }

    public String getInvoiceOriginalName() { return invoiceOriginalName; }
    public void setInvoiceOriginalName(String invoiceOriginalName) { this.invoiceOriginalName = invoiceOriginalName; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
