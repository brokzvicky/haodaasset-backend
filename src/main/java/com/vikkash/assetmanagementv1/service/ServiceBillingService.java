package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import com.vikkash.assetmanagementv1.exception.ResourceNotFoundException;
import com.vikkash.assetmanagementv1.repository.ServiceBillingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All Service Billing business logic lives here: CRUD for payment records
 * plus secure storage/retrieval of the uploaded PDF invoices.
 *
 * Invoice files are stored on disk under {@link #uploadDir}, named with a
 * random UUID (never the original filename) to avoid path-traversal /
 * collision issues. Only the resulting relative path is persisted on the
 * entity — the file bytes never touch the database.
 */
@Service
public class ServiceBillingService {

    private static final Logger log = LoggerFactory.getLogger(ServiceBillingService.class);

    private static final List<String> VALID_STATUSES = List.of("Paid", "Pending", "Overdue");

    private final ServiceBillingRepository repository;
    private final AuditLogService auditLogService;

    // Root folder for invoice storage. Configurable so deployments can point
    // this at a persistent disk/volume; defaults to a local folder for dev.
    @Value("${app.storage.invoice-upload-dir:uploads/invoices}")
    private String uploadDir;

    public ServiceBillingService(ServiceBillingRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
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

    @Transactional
    public ServiceBilling create(String service, String vendor, BigDecimal amount, LocalDate paymentDate,
                                  String status, String remarks, MultipartFile invoiceFile) {
        ServiceBilling billing = new ServiceBilling();
        billing.setService(requireText(service, "Service"));
        billing.setVendor(requireText(vendor, "Vendor"));
        billing.setAmount(requireAmount(amount));
        billing.setPaymentDate(requirePaymentDate(paymentDate));
        billing.setStatus(validateStatus(status));
        billing.setRemarks(remarks);

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            storeInvoice(billing, invoiceFile);
        }

        ServiceBilling saved = repository.save(billing);
        log.info("Created service payment id={} service={} vendor={} amount={}", saved.getId(), saved.getService(), saved.getVendor(), saved.getAmount());
        auditLogService.record("SERVICE_BILLING", String.valueOf(saved.getId()), "CREATED",
                "Added service payment for '" + saved.getService() + "' (" + saved.getVendor() + ")");
        return saved;
    }

    // ── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public ServiceBilling update(Long id, String service, String vendor, BigDecimal amount, LocalDate paymentDate,
                                  String status, String remarks, MultipartFile invoiceFile) {
        ServiceBilling billing = getById(id);

        if (service != null && !service.isBlank()) billing.setService(service.trim());
        if (vendor != null && !vendor.isBlank()) billing.setVendor(vendor.trim());
        if (amount != null) billing.setAmount(requireAmount(amount));
        if (paymentDate != null) billing.setPaymentDate(paymentDate);
        if (status != null && !status.isBlank()) billing.setStatus(validateStatus(status));
        if (remarks != null) billing.setRemarks(remarks);

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            deleteInvoiceFileQuietly(billing.getInvoicePath());
            storeInvoice(billing, invoiceFile);
        }

        ServiceBilling saved = repository.save(billing);
        log.info("Updated service payment id={}", id);
        auditLogService.record("SERVICE_BILLING", String.valueOf(saved.getId()), "UPDATED",
                "Updated service payment for '" + saved.getService() + "' (" + saved.getVendor() + ")");
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

    /** Resolves the absolute path of a stored invoice, verifying it exists on disk. */
    @Transactional(readOnly = true)
    public Path resolveInvoiceFile(Long id) {
        ServiceBilling billing = getById(id);
        if (billing.getInvoicePath() == null || billing.getInvoicePath().isBlank()) {
            throw new ResourceNotFoundException("No invoice has been uploaded for this payment.");
        }
        Path path = uploadRoot().resolve(billing.getInvoicePath()).normalize();
        if (!path.startsWith(uploadRoot()) || !Files.exists(path)) {
            throw new ResourceNotFoundException("The invoice file could not be found on the server.");
        }
        return path;
    }

    @Transactional(readOnly = true)
    public String invoiceDownloadName(Long id) {
        ServiceBilling billing = getById(id);
        return billing.getInvoiceOriginalName() != null
                ? billing.getInvoiceOriginalName()
                : "invoice-" + id + ".pdf";
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void storeInvoice(ServiceBilling billing, MultipartFile file) {
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.pdf";

        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType)
                || originalName.toLowerCase().endsWith(".pdf");
        if (!isPdf) {
            throw new IllegalArgumentException("Only PDF files are allowed for invoices.");
        }

        try {
            Path root = uploadRoot();
            Files.createDirectories(root);

            String storedName = UUID.randomUUID() + ".pdf";
            Path destination = root.resolve(storedName).normalize();
            if (!destination.startsWith(root)) {
                throw new IllegalArgumentException("Invalid invoice file name.");
            }

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            billing.setInvoicePath(storedName);
            billing.setInvoiceOriginalName(originalName);
        } catch (IOException e) {
            log.error("Failed to store invoice file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to store the uploaded invoice. Please try again.");
        }
    }

    private void deleteInvoiceFileQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        try {
            Path path = uploadRoot().resolve(storedName).normalize();
            if (path.startsWith(uploadRoot())) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete old invoice file '{}': {}", storedName, e.getMessage());
        }
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
