package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.dto.InvoiceExtractionResult;
import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import com.vikkash.assetmanagementv1.service.InvoiceExtractionService;
import com.vikkash.assetmanagementv1.service.ServiceBillingReportService;
import com.vikkash.assetmanagementv1.service.ServiceBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private static final Logger log = LoggerFactory.getLogger(ServiceBillingController.class);

    private final ServiceBillingService service;
    private final ServiceBillingReportService reportService;
    private final InvoiceExtractionService invoiceExtractionService;

    public ServiceBillingController(ServiceBillingService service, ServiceBillingReportService reportService,
                                     InvoiceExtractionService invoiceExtractionService) {
        this.service = service;
        this.reportService = reportService;
        this.invoiceExtractionService = invoiceExtractionService;
    }

    /**
     * Invoice PDF Auto-Fill (Service Billing only): reads an uploaded invoice
     * PDF and returns whatever fields could be confidently identified — it
     * never creates or touches a billing record. The frontend uses this to
     * fill blank form fields for the admin to review before saving; any
     * field left null here simply stays blank/untouched on the form.
     * Supports both text-based and scanned (OCR) invoice PDFs.
     */
    @PostMapping(value = "/extract-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceExtractionResult> extractInvoice(
            @RequestParam("invoiceFile") MultipartFile invoiceFile) {
        return ResponseEntity.ok(invoiceExtractionService.extract(invoiceFile));
    }

    @GetMapping
    public List<ServiceBilling> getAll() {
        return service.getAll();
    }

    /**
     * Search/filter (Requirement 9): by service, vendor, billing period
     * (periodFrom/periodTo overlap), status, and exact payment date. Any
     * param can be omitted. Powers both the list view's filter bar and the
     * PDF/Excel export so exports always match what's on screen.
     */
    @GetMapping("/search")
    public List<ServiceBilling> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String periodFrom,
            @RequestParam(required = false) String periodTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentDate
    ) {
        return this.service.search(service, vendor, parseDateOrNull(periodFrom), parseDateOrNull(periodTo),
                status, parseDateOrNull(paymentDate));
    }

    /** Billing History (Requirement 6): every past billing period for one service+vendor, latest first. */
    @GetMapping("/history")
    public List<ServiceBilling> history(@RequestParam String service, @RequestParam String vendor) {
        return this.service.getHistory(service, vendor);
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
     * Creates a new billing record for a billing period. Accepts
     * multipart/form-data so the invoice can be uploaded in the same
     * request. Never overwrites an existing record — every call (including
     * "Re-Add Billing" for a service with prior history) inserts a new row
     * (Requirement 1) and is rejected with 409 if the exact
     * service+vendor+period combination already exists (Requirement 7).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> create(
            @RequestParam String service,
            @RequestParam String vendor,
            @RequestParam("billingFromDate") String billingFromDate,
            @RequestParam("billingToDate") String billingToDate,
            @RequestParam BigDecimal amount,
            @RequestParam("paymentDate") String paymentDate,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false, defaultValue = "Pending") String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String invoiceDate,
            @RequestParam(required = false) BigDecimal gstAmount,
            @RequestParam(required = false) BigDecimal totalAmount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String invoiceReference,
            @RequestParam(required = false) String serviceDetails
    ) {
        ServiceBilling created = this.service.create(
                service, vendor, LocalDate.parse(billingFromDate), LocalDate.parse(billingToDate),
                amount, LocalDate.parse(paymentDate), parseDateOrNull(dueDate), status, remarks, invoiceFile,
                invoiceNumber, parseDateOrNull(invoiceDate), gstAmount, totalAmount, currency,
                invoiceReference, serviceDetails);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * Updates an existing billing record in place (e.g. correcting a typo or
     * marking it Paid) — distinct from "Re-Add Billing", which always
     * creates a new record. All fields are optional here — only
     * non-null/non-blank values overwrite the existing record — and a new
     * invoice file (if provided) replaces the old one for this record only.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> update(
            @PathVariable Long id,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String vendor,
            @RequestParam(value = "billingFromDate", required = false) String billingFromDate,
            @RequestParam(value = "billingToDate", required = false) String billingToDate,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(value = "paymentDate", required = false) String paymentDate,
            @RequestParam(value = "dueDate", required = false) String dueDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String remarks,
            @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String invoiceDate,
            @RequestParam(required = false) BigDecimal gstAmount,
            @RequestParam(required = false) BigDecimal totalAmount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String invoiceReference,
            @RequestParam(required = false) String serviceDetails
    ) {
        ServiceBilling updated = this.service.update(
                id, service, vendor, parseDateOrNull(billingFromDate), parseDateOrNull(billingToDate),
                amount, parseDateOrNull(paymentDate), parseDateOrNull(dueDate), status, remarks, invoiceFile,
                invoiceNumber, parseDateOrNull(invoiceDate), gstAmount, totalAmount, currency,
                invoiceReference, serviceDetails);
        return ResponseEntity.ok(updated);
    }

    /** PDF export (Requirement 10). Accepts the same filters as /search so it always matches the current view. */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String periodFrom,
            @RequestParam(required = false) String periodTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentDate
    ) throws IOException {
        List<ServiceBilling> records = this.service.search(service, vendor, parseDateOrNull(periodFrom),
                parseDateOrNull(periodTo), status, parseDateOrNull(paymentDate));
        byte[] pdf = reportService.generatePdf(records);
        String filename = "service-billing-report-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /** Excel export (Requirement 10). Accepts the same filters as /search so it always matches the current view. */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String periodFrom,
            @RequestParam(required = false) String periodTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentDate
    ) throws IOException {
        List<ServiceBilling> records = this.service.search(service, vendor, parseDateOrNull(periodFrom),
                parseDateOrNull(periodTo), status, parseDateOrNull(paymentDate));
        byte[] excel = reportService.generateExcel(records);
        String filename = "service-billing-report-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(excel);
    }

    private static LocalDate parseDateOrNull(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }

    /** Uploads or replaces just the invoice for an existing payment, without touching other fields. */
    @PostMapping(value = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceBilling> uploadInvoice(@PathVariable Long id,
                                                          @RequestParam("invoiceFile") MultipartFile invoiceFile) {
        return ResponseEntity.ok(service.uploadInvoice(id, invoiceFile));
    }

    /**
     * Streams the PDF invoice inline, directly from Amazon S3, so the browser
     * can preview it (e.g. in a new tab). The object is never buffered fully
     * in memory or written to local disk — bytes flow straight from the S3
     * response stream into the HTTP response body.
     */
    @GetMapping("/{id}/invoice/view")
    public ResponseEntity<InputStreamResource> viewInvoice(@PathVariable Long id) {
        return streamInvoice(id, "inline");
    }

    /** Streams the PDF invoice as a downloadable attachment, directly from Amazon S3. */
    @GetMapping("/{id}/invoice/download")
    public ResponseEntity<InputStreamResource> downloadInvoice(@PathVariable Long id) {
        return streamInvoice(id, "attachment");
    }

    private ResponseEntity<InputStreamResource> streamInvoice(Long id, String disposition) {
        ResponseInputStream<GetObjectResponse> s3Stream = service.streamInvoiceFile(id);
        long contentLength = s3Stream.response().contentLength() != null ? s3Stream.response().contentLength() : -1;

        log.info("Streaming invoice id={} from S3 (disposition={}, bytes={})", id, disposition, contentLength);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + service.invoiceDownloadName(id) + "\"");
        if (contentLength >= 0) {
            responseBuilder.contentLength(contentLength);
        }
        return responseBuilder.body(new InputStreamResource(s3Stream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Service payment deleted successfully"));
    }
}
