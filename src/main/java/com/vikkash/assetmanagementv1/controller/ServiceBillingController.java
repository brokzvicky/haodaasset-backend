package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import com.vikkash.assetmanagementv1.service.ServiceBillingService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the Service Billing module.
 * Mapped under /api/admin/** so Spring Security's ADMIN role guard
 * (SecurityConfig, "/api/admin/**" -> hasRole("ADMIN")) applies automatically
 * — no separate security rule needed, same pattern as ReportController.
 *
 * CORS is handled centrally by SecurityConfig.corsConfigurationSource().
 */
@RestController
@RequestMapping("/api/admin/service-billing")
public class ServiceBillingController {

    private final ServiceBillingService service;

    public ServiceBillingController(ServiceBillingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServiceBilling> getAll() {
        return service.getAll();
    }

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard() {
        return service.getDashboardStats();
    }

    @GetMapping("/{id}")
    public ServiceBilling getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * Creates a new service payment. Accepts multipart/form-data so the
     * optional PDF invoice can be uploaded in the same request as the
     * payment details.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> create(
            @RequestParam String service,
            @RequestParam String vendor,
            @RequestParam BigDecimal amount,
            @RequestParam("paymentDate") String paymentDate,
            @RequestParam(required = false, defaultValue = "Pending") String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile
    ) {
        ServiceBilling created = this.service.create(
                service, vendor, amount, LocalDate.parse(paymentDate), status, remarks, invoiceFile);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * Updates an existing service payment. All fields are optional here —
     * only non-null/non-blank values overwrite the existing record — and a
     * new invoice file (if provided) replaces the old one.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> update(
            @PathVariable Long id,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(value = "paymentDate", required = false) String paymentDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile
    ) {
        LocalDate parsedDate = (paymentDate != null && !paymentDate.isBlank()) ? LocalDate.parse(paymentDate) : null;
        ServiceBilling updated = this.service.update(
                id, service, vendor, amount, parsedDate, status, remarks, invoiceFile);
        return ResponseEntity.ok(updated);
    }

    /** Uploads or replaces just the invoice for an existing payment, without touching other fields. */
    @PostMapping(value = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> uploadInvoice(@PathVariable Long id,
                                                          @RequestParam("invoiceFile") MultipartFile invoiceFile) {
        return ResponseEntity.ok(service.uploadInvoice(id, invoiceFile));
    }

    /** Streams the PDF invoice inline so the browser can preview it (e.g. in a new tab). */
    @GetMapping("/{id}/invoice/view")
    public ResponseEntity<FileSystemResource> viewInvoice(@PathVariable Long id) {
        Path file = service.resolveInvoiceFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + service.invoiceDownloadName(id) + "\"")
                .body(new FileSystemResource(file));
    }

    /** Streams the PDF invoice as a downloadable attachment. */
    @GetMapping("/{id}/invoice/download")
    public ResponseEntity<FileSystemResource> downloadInvoice(@PathVariable Long id) {
        Path file = service.resolveInvoiceFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + service.invoiceDownloadName(id) + "\"")
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Service payment deleted successfully"));
    }
}
