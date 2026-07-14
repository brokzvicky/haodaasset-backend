package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Mapped under /api/admin/** so Spring Security's ADMIN role guard
 * (SecurityConfig) applies automatically — no separate security rule needed.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * GET /api/admin/reports/employee-asset-report/pdf
     * Streams a PDF listing every employee and their currently-assigned
     * assets. Powers the "Employee Asset Report (PDF)" button on Reports.
     */
    @GetMapping("/employee-asset-report/pdf")
    public ResponseEntity<byte[]> employeeAssetReportPdf() throws IOException {
        byte[] pdf = reportService.generateEmployeeAssetReportPdf();
        String filename = "employee-asset-report-" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
