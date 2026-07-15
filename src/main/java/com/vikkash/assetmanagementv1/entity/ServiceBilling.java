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
        @Index(name = "idx_service_billing_vendor",        columnList = "vendor"),
        @Index(name = "idx_service_billing_period",        columnList = "service, vendor, billing_from_date, billing_to_date")
    },
    uniqueConstraints = {
        // Belt-and-braces DB-level guard against the same service+vendor
        // being billed twice for an identical period. The service layer
        // also checks this explicitly first so it can surface a friendly
        // "Billing for this period already exists." message instead of a
        // raw constraint-violation error.
        @UniqueConstraint(
            name = "uk_service_billing_period",
            columnNames = {"service", "vendor", "billing_from_date", "billing_to_date"}
        )
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

    /** First day of the billing period this record covers, e.g. 01 Jun 2026. */
    @NotNull(message = "Billing From Date is required")
    @Column(name = "billing_from_date")
    private LocalDate billingFromDate;

    /** Last day of the billing period this record covers, e.g. 30 Jun 2026. */
    @NotNull(message = "Billing To Date is required")
    @Column(name = "billing_to_date")
    private LocalDate billingToDate;

    @NotNull(message = "Amount is required")
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /** Optional — when this period's payment is due. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** One of: Paid, Pending, Overdue. */
    @NotBlank(message = "Status is required")
    private String status = "Pending";

    /** Relative path (under the configured upload root) to the stored PDF invoice, if any. */
    @Column(name = "invoice_path")
    private String invoicePath;

    /** Original filename of the uploaded invoice, kept for a friendlier download name. */
    @Column(name = "invoice_original_name")
    private String invoiceOriginalName;

    // ── Invoice PDF Auto-Fill fields (Requirement: "Invoice PDF Auto-Fill").
    // All optional — populated from the uploaded invoice when confidently
    // detected, otherwise left null for manual entry. ─────────────────────

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "gst_amount", precision = 14, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** ISO-ish currency label, e.g. "INR", "USD". */
    private String currency;

    @Column(name = "invoice_reference")
    private String invoiceReference;

    /** Description / service details as printed on the invoice (distinct from the admin's own free-text "remarks"). */
    @Column(name = "service_details", columnDefinition = "TEXT")
    private String serviceDetails;

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

    public LocalDate getBillingFromDate() { return billingFromDate; }
    public void setBillingFromDate(LocalDate billingFromDate) { this.billingFromDate = billingFromDate; }

    public LocalDate getBillingToDate() { return billingToDate; }
    public void setBillingToDate(LocalDate billingToDate) { this.billingToDate = billingToDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    /** Human-friendly unique billing reference, e.g. "SB-000123". Derived from the DB id — never reused, never overwritten. */
    public String getBillingId() { return id == null ? null : String.format("SB-%06d", id); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInvoicePath() { return invoicePath; }
    public void setInvoicePath(String invoicePath) { this.invoicePath = invoicePath; }

    public String getInvoiceOriginalName() { return invoiceOriginalName; }
    public void setInvoiceOriginalName(String invoiceOriginalName) { this.invoiceOriginalName = invoiceOriginalName; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInvoiceReference() { return invoiceReference; }
    public void setInvoiceReference(String invoiceReference) { this.invoiceReference = invoiceReference; }

    public String getServiceDetails() { return serviceDetails; }
    public void setServiceDetails(String serviceDetails) { this.serviceDetails = serviceDetails; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
