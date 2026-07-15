package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.ServiceBilling;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the Service Billing export reports (Requirement 10: "Support
 * exporting reports to PDF and Excel"). Takes an already-filtered list of
 * {@link ServiceBilling} records — filtering itself (by service, vendor,
 * billing period, status, payment date) happens in
 * {@link ServiceBillingService#search}, so the export always matches
 * whatever the admin currently has on screen.
 *
 * Requires the following Maven dependencies (mirrors the note on
 * ReportService for PDFBox; poi-ooxml additionally needed for the Excel
 * export — add to pom.xml if not already present):
 *   <dependency>
 *     <groupId>org.apache.pdfbox</groupId>
 *     <artifactId>pdfbox</artifactId>
 *     <version>2.0.31</version>
 *   </dependency>
 *   <dependency>
 *     <groupId>org.apache.poi</groupId>
 *     <artifactId>poi-ooxml</artifactId>
 *     <version>5.2.5</version>
 *   </dependency>
 */
@Service
public class ServiceBillingReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final float MARGIN_LEFT   = 40f;
    private static final float MARGIN_TOP    = 50f;
    private static final float MARGIN_BOTTOM = 45f;
    private static final float PAGE_WIDTH    = PDRectangle.A4.rotate().getWidth();
    private static final float PAGE_HEIGHT   = PDRectangle.A4.rotate().getHeight();

    // ── PDF export ───────────────────────────────────────────────────────────

    public byte[] generatePdf(List<ServiceBilling> records) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PdfCursor cursor = new PdfCursor(document, regular, bold);
            cursor.newPage();
            cursor.title("Service Billing & Invoice Report");
            cursor.subtitle("Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    + "  ·  " + records.size() + " record" + (records.size() == 1 ? "" : "s"));
            cursor.tableHeader();

            for (ServiceBilling r : records) {
                cursor.ensureSpace(16);
                cursor.row(r);
            }
            cursor.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static class PdfCursor {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float y;

        PdfCursor(PDDocument document, PDFont regular, PDFont bold) {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
        }

        void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4.rotate());
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM) {
                newPage();
                tableHeader();
            }
        }

        void title(String text) throws IOException {
            at(text, bold, 16, MARGIN_LEFT);
            y -= 20;
        }

        void subtitle(String text) throws IOException {
            at(text, regular, 9, MARGIN_LEFT);
            y -= 16;
        }

        private float[] cols() {
            return new float[]{40, 150, 260, 370, 470, 550, 640, 720};
        }

        void tableHeader() throws IOException {
            float[] c = cols();
            String[] labels = {"BILLING ID", "SERVICE", "VENDOR", "PERIOD", "AMOUNT", "PAYMENT DATE", "DUE DATE", "STATUS"};
            stream.setNonStrokingColor(90, 90, 90);
            for (int i = 0; i < labels.length; i++) at(labels[i], bold, 7.5f, MARGIN_LEFT + c[i]);
            stream.setNonStrokingColor(0, 0, 0);
            y -= 10;
            rule();
            y -= 4;
        }

        void row(ServiceBilling r) throws IOException {
            float[] c = cols();
            String period = fmt(r.getBillingFromDate()) + " - " + fmt(r.getBillingToDate());
            at(safe(r.getBillingId()), regular, 8, MARGIN_LEFT + c[0]);
            at(truncate(safe(r.getService()), 18), regular, 8, MARGIN_LEFT + c[1]);
            at(truncate(safe(r.getVendor()), 18), regular, 8, MARGIN_LEFT + c[2]);
            at(period, regular, 8, MARGIN_LEFT + c[3]);
            at(formatAmount(r.getAmount()), regular, 8, MARGIN_LEFT + c[4]);
            at(fmt(r.getPaymentDate()), regular, 8, MARGIN_LEFT + c[5]);
            at(fmt(r.getDueDate()), regular, 8, MARGIN_LEFT + c[6]);
            at(safe(r.getStatus()), regular, 8, MARGIN_LEFT + c[7]);
            y -= 15;
        }

        private void at(String text, PDFont font, float size, float x) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(sanitize(text));
            stream.endText();
        }

        private void rule() throws IOException {
            stream.setLineWidth(0.5f);
            stream.setStrokingColor(210, 210, 210);
            stream.moveTo(MARGIN_LEFT, y);
            stream.lineTo(PAGE_WIDTH - MARGIN_LEFT, y);
            stream.stroke();
            stream.setStrokingColor(0, 0, 0);
        }

        void close() throws IOException {
            if (stream != null) stream.close();
        }

        private static String fmt(java.time.LocalDate d) { return d == null ? "-" : d.format(DATE_FMT); }
        private static String safe(String s) { return (s == null || s.isBlank()) ? "-" : s; }
        private static String truncate(String v, int max) { return v.length() > max ? v.substring(0, max - 1) + "…" : v; }
        private static String formatAmount(BigDecimal amount) { return amount == null ? "-" : "Rs " + amount.toPlainString(); }
        private static String sanitize(String text) { return text == null ? "" : text.replaceAll("[^\\x00-\\xFF]", "?"); }
    }

    // ── Excel export ─────────────────────────────────────────────────────────

    public byte[] generateExcel(List<ServiceBilling> records) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Service Billing");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd-mmm-yyyy"));

            String[] headers = {
                    "Billing ID", "Service", "Vendor", "Billing From Date", "Billing To Date",
                    "Amount", "Payment Date", "Due Date", "Status", "Invoice Uploaded", "Remarks"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ServiceBilling r : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getBillingId() == null ? "" : r.getBillingId());
                row.createCell(1).setCellValue(r.getService() == null ? "" : r.getService());
                row.createCell(2).setCellValue(r.getVendor() == null ? "" : r.getVendor());
                setDateCell(row.createCell(3), r.getBillingFromDate(), dateStyle);
                setDateCell(row.createCell(4), r.getBillingToDate(), dateStyle);
                row.createCell(5).setCellValue(r.getAmount() == null ? 0 : r.getAmount().doubleValue());
                setDateCell(row.createCell(6), r.getPaymentDate(), dateStyle);
                setDateCell(row.createCell(7), r.getDueDate(), dateStyle);
                row.createCell(8).setCellValue(r.getStatus() == null ? "" : r.getStatus());
                row.createCell(9).setCellValue(r.getInvoicePath() != null && !r.getInvoicePath().isBlank() ? "Yes" : "No");
                row.createCell(10).setCellValue(r.getRemarks() == null ? "" : r.getRemarks());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setDateCell(Cell cell, java.time.LocalDate date, CellStyle dateStyle) {
        if (date == null) {
            cell.setCellValue("-");
        } else {
            cell.setCellValue(java.sql.Date.valueOf(date));
            cell.setCellStyle(dateStyle);
        }
    }
}
