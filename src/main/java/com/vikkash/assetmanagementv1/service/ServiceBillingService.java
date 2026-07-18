package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import com.vikkash.assetmanagementv1.exception.DuplicateResourceException;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.ServiceBillingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * All Service Billing business logic lives here: CRUD for payment records
 * plus secure storage/retrieval of the uploaded PDF invoices.
 *
 * Physical file storage is delegated to {@link S3StorageService}, which
 * uploads to Amazon S3 (bucket configured via {@code aws.s3.bucket-name}) —
 * never to local disk — so invoices survive redeploys/restarts on platforms
 * like Render whose local filesystem is ephemeral. Only the resulting S3
 * OBJECT KEY (a random UUID under the "invoices/" prefix, never the original
 * filename) is persisted on the entity, in the existing {@code invoicePath}
 * column — the file bytes never touch the database, and no machine-specific
 * absolute path is ever stored.
 */
@Service
public class ServiceBillingService {

    private static final Logger log = LoggerFactory.getLogger(ServiceBillingService.class);

    private static final List<String> VALID_STATUSES = List.of("Paid", "Pending", "Overdue");

    private final ServiceBillingRepository repository;
    private final AuditLogService auditLogService;
    private final S3StorageService s3StorageService;

    public ServiceBillingService(ServiceBillingRepository repository, AuditLogService auditLogService,
                                  S3StorageService s3StorageService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.s3StorageService = s3StorageService;
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ServiceBilling> getAll() {
        return repository.findAllByOrderByPaymentDateDesc();
    }

    @Transactional(readOnly = true)
    public ServiceBilling getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service payment not found with id: " + id));
    }

    /** Billing History for a service+vendor: every past billing period, latest first (Requirement 6). */
    @Transactional(readOnly = true)
    public List<ServiceBilling> getHistory(String service, String vendor) {
        return repository.findByServiceIgnoreCaseAndVendorIgnoreCaseOrderByBillingFromDateDesc(
                requireText(service, "Service"), requireText(vendor, "Vendor"));
    }

    /**
     * Search/filter used by the list view and by PDF/Excel export
     * (Requirement 9): by service, vendor, billing period (overlap with the
     * given range), status, and payment date. Any parameter left null/blank
     * is ignored.
     */
    @Transactional(readOnly = true)
    public List<ServiceBilling> search(String service, String vendor, LocalDate periodFrom, LocalDate periodTo,
                                        String status, LocalDate paymentDate) {
        return repository.findAllByOrderByPaymentDateDesc().stream()
                .filter(r -> service == null || service.isBlank() || equalsIgnoreCase(r.getService(), service))
                .filter(r -> vendor == null || vendor.isBlank() || equalsIgnoreCase(r.getVendor(), vendor))
                .filter(r -> periodFrom == null || (r.getBillingToDate() != null && !r.getBillingToDate().isBefore(periodFrom)))
                .filter(r -> periodTo == null || (r.getBillingFromDate() != null && !r.getBillingFromDate().isAfter(periodTo)))
                .filter(r -> status == null || status.isBlank() || "All".equalsIgnoreCase(status) || equalsIgnoreCase(r.getStatus(), status))
                .filter(r -> paymentDate == null || paymentDate.equals(r.getPaymentDate()))
                .toList();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    // ── Dashboard ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getDashboardStats() {
        return Map.of(
                "totalPayments",     repository.count(),
                "paidServices",      repository.countByStatus("Paid"),
                "pendingServices",   repository.countByStatus("Pending"),
                "overdueServices",   repository.countByStatus("Overdue"),
                "totalInvoicesUploaded", repository.countByInvoicePathIsNotNull()
        );
    }

    // ── Create ─────────────────────────────────────────────────────────────

    /**
     * Creates a brand-new billing record. Every call — including a "Re-Add
     * Billing" for a service that already has payment history — always
     * inserts a fresh row (Requirement 1); existing records are never
     * touched. Rejects a period that's already been billed for this exact
     * service+vendor (Requirement 7).
     */
    @Transactional
    public ServiceBilling create(String service, String vendor, LocalDate billingFromDate, LocalDate billingToDate,
                                  BigDecimal amount, LocalDate paymentDate, LocalDate dueDate,
                                  String status, String remarks, MultipartFile invoiceFile,
                                  String invoiceNumber, LocalDate invoiceDate, BigDecimal gstAmount,
                                  BigDecimal totalAmount, String currency, String invoiceReference,
                                  String serviceDetails) {
        String svc = requireText(service, "Service");
        String vnd = requireText(vendor, "Vendor");
        LocalDate from = requireBillingDate(billingFromDate, "Billing From Date");
        LocalDate to = requireBillingDate(billingToDate, "Billing To Date");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Billing To Date cannot be before Billing From Date.");
        }

        if (repository.existsByServiceIgnoreCaseAndVendorIgnoreCaseAndBillingFromDateAndBillingToDate(svc, vnd, from, to)) {
            throw new DuplicateResourceException("Billing for this period already exists.");
        }

        ServiceBilling billing = new ServiceBilling();
        billing.setService(svc);
        billing.setVendor(vnd);
        billing.setBillingFromDate(from);
        billing.setBillingToDate(to);
        billing.setAmount(requireAmount(amount));
        billing.setPaymentDate(requirePaymentDate(paymentDate));
        billing.setDueDate(dueDate);
        billing.setStatus(validateStatus(status));
        billing.setRemarks(remarks);
        billing.setInvoiceNumber(blankToNull(invoiceNumber));
        billing.setInvoiceDate(invoiceDate);
        billing.setGstAmount(gstAmount);
        billing.setTotalAmount(totalAmount);
        billing.setCurrency(blankToNull(currency));
        billing.setInvoiceReference(blankToNull(invoiceReference));
        billing.setServiceDetails(blankToNull(serviceDetails));

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            storeInvoice(billing, invoiceFile);
        }

        ServiceBilling saved = repository.save(billing);
        log.info("Created service billing id={} service={} vendor={} period={}..{} amount={}",
                saved.getId(), saved.getService(), saved.getVendor(), from, to, saved.getAmount());
        auditLogService.record("SERVICE_BILLING", String.valueOf(saved.getId()), "CREATED",
                "Added billing record for '" + saved.getService() + "' (" + saved.getVendor() + ") period " + from + " to " + to);
        return saved;
    }

    // ── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public ServiceBilling update(Long id, String service, String vendor, LocalDate billingFromDate, LocalDate billingToDate,
                                  BigDecimal amount, LocalDate paymentDate, LocalDate dueDate,
                                  String status, String remarks, MultipartFile invoiceFile,
                                  String invoiceNumber, LocalDate invoiceDate, BigDecimal gstAmount,
                                  BigDecimal totalAmount, String currency, String invoiceReference,
                                  String serviceDetails) {
        ServiceBilling billing = getById(id);

        if (service != null && !service.isBlank()) billing.setService(service.trim());
        if (vendor != null && !vendor.isBlank()) billing.setVendor(vendor.trim());

        LocalDate newFrom = billingFromDate != null ? billingFromDate : billing.getBillingFromDate();
        LocalDate newTo = billingToDate != null ? billingToDate : billing.getBillingToDate();
        if ((billingFromDate != null || billingToDate != null) && newFrom != null && newTo != null) {
            if (newTo.isBefore(newFrom)) {
                throw new IllegalArgumentException("Billing To Date cannot be before Billing From Date.");
            }
            if (repository.existsByServiceIgnoreCaseAndVendorIgnoreCaseAndBillingFromDateAndBillingToDateAndIdNot(
                    billing.getService(), billing.getVendor(), newFrom, newTo, id)) {
                throw new DuplicateResourceException("Billing for this period already exists.");
            }
            billing.setBillingFromDate(newFrom);
            billing.setBillingToDate(newTo);
        }

        if (amount != null) billing.setAmount(requireAmount(amount));
        if (paymentDate != null) billing.setPaymentDate(paymentDate);
        if (dueDate != null) billing.setDueDate(dueDate);
        if (status != null && !status.isBlank()) billing.setStatus(validateStatus(status));
        if (remarks != null) billing.setRemarks(remarks);
        if (invoiceNumber != null) billing.setInvoiceNumber(blankToNull(invoiceNumber));
        if (invoiceDate != null) billing.setInvoiceDate(invoiceDate);
        if (gstAmount != null) billing.setGstAmount(gstAmount);
        if (totalAmount != null) billing.setTotalAmount(totalAmount);
        if (currency != null) billing.setCurrency(blankToNull(currency));
        if (invoiceReference != null) billing.setInvoiceReference(blankToNull(invoiceReference));
        if (serviceDetails != null) billing.setServiceDetails(blankToNull(serviceDetails));

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            deleteInvoiceFileQuietly(billing.getInvoicePath());
            storeInvoice(billing, invoiceFile);
        }

        ServiceBilling saved = repository.save(billing);
        log.info("Updated service billing id={}", id);
        auditLogService.record("SERVICE_BILLING", String.valueOf(saved.getId()), "UPDATED",
                "Updated billing record for '" + saved.getService() + "' (" + saved.getVendor() + ")");
        return saved;
    }

    // ── Upload / replace invoice only ───────────────────────────────────────

    @Transactional
    public ServiceBilling uploadInvoice(Long id, MultipartFile invoiceFile) {
        if (invoiceFile == null || invoiceFile.isEmpty()) {
            throw new IllegalArgumentException("Please choose a PDF invoice to upload.");
        }
        ServiceBilling billing = getById(id);
        deleteInvoiceFileQuietly(billing.getInvoicePath());
        storeInvoice(billing, invoiceFile);
        ServiceBilling saved = repository.save(billing);
        auditLogService.record("SERVICE_BILLING", String.valueOf(saved.getId()), "INVOICE_UPLOADED",
                "Uploaded invoice for '" + saved.getService() + "' (" + saved.getVendor() + ")");
        return saved;
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        ServiceBilling billing = getById(id);
        deleteInvoiceFileQuietly(billing.getInvoicePath());
        repository.deleteById(id);
        log.warn("Deleted service payment id={}", id);
        auditLogService.record("SERVICE_BILLING", String.valueOf(id), "DELETED",
                "Deleted service payment for '" + billing.getService() + "' (" + billing.getVendor() + ")");
    }

    // ── Invoice file access (view / download) ───────────────────────────────

    /**
     * Opens a live stream of the stored invoice directly from Amazon S3,
     * verifying the object still exists before returning it, so the View and
     * Download endpoints can pipe the bytes straight through to the browser
     * without ever buffering the whole file on the backend or writing it to
     * local disk.
     *
     * The caller (the controller) must close the returned stream once the
     * HTTP response body has been written.
     */
    @Transactional(readOnly = true)
    public ResponseInputStream<GetObjectResponse> streamInvoiceFile(Long id) {
        ServiceBilling billing = getById(id);
        String key = billing.getInvoicePath();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("No invoice has been uploaded for this payment.");
        }
        if (!s3StorageService.exists(key)) {
            throw new ResourceNotFoundException(
                    "The invoice file for this payment is missing from cloud storage (S3 key '" + key + "'). "
                            + "It may have been deleted or moved since it was uploaded. Please re-upload the invoice.");
        }
        return s3StorageService.downloadFile(key);
    }

    @Transactional(readOnly = true)
    public String invoiceDownloadName(Long id) {
        ServiceBilling billing = getById(id);
        return billing.getInvoiceOriginalName() != null
                ? billing.getInvoiceOriginalName()
                : "invoice-" + id + ".pdf";
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Validates and uploads the file to Amazon S3 via {@link S3StorageService},
     * then records only the returned S3 object key (a random UUID under the
     * "invoices/" prefix) and the original filename (for download/display
     * purposes only — never used to locate the object in S3) on the entity.
     */
    private void storeInvoice(ServiceBilling billing, MultipartFile file) {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.pdf";
        String key = s3StorageService.uploadFile(file); // throws IllegalArgumentException for bad type/size
        billing.setInvoicePath(key);
        billing.setInvoiceOriginalName(originalName);
    }

    private void deleteInvoiceFileQuietly(String key) {
        s3StorageService.deleteFile(key);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount is required and must be zero or greater.");
        }
        return amount;
    }

    private LocalDate requirePaymentDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Payment date is required.");
        }
        return date;
    }

    private LocalDate requireBillingDate(LocalDate date, String label) {
        if (date == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return date;
    }

    private String validateStatus(String status) {
        if (status == null || status.isBlank()) return "Pending";
        String trimmed = status.trim();
        boolean valid = VALID_STATUSES.stream().anyMatch(s -> s.equalsIgnoreCase(trimmed));
        if (!valid) {
            throw new IllegalArgumentException("Status must be one of: Paid, Pending, Overdue.");
        }
        // Normalize to canonical casing
        return VALID_STATUSES.stream().filter(s -> s.equalsIgnoreCase(trimmed)).findFirst().orElse("Pending");
    }
}
