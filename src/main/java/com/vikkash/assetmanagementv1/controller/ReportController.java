package com.vikkash.assetmanagementv1.controller;

import com.vikkash.assetmanagementv1.service.AnalyticsService;
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
import java.util.Map;

/**
 * Mapped under /api/admin/** so Spring Security's ADMIN role guard
 * (SecurityConfig) applies automatically — no separate security rule needed.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reportService;
    private final AnalyticsService analyticsService;

    public ReportController(ReportService reportService, AnalyticsService analyticsService) {
        this.reportService = reportService;
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/admin/reports/analytics
     * Chart-ready aggregate statistics (counts by status/type/location/brand,
     * warranty expiry watchlist, maintenance stats, asset value totals, age
     * brackets) — powers the Reports & Analytics page and Dashboard widgets.
     */
    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return analyticsService.getAnalytics();
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
