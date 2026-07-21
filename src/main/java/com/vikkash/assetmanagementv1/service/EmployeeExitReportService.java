package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Employee;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the Employee Exit Report (PDF + Excel), following the same
 * PDFBox/POI table-drawing pattern as {@link ServiceBillingReportService}
 * for visual and code consistency across the app's exports.
 *
 * Takes an already-fetched list of employees who are anywhere in the
 * separation pipeline (Notice Period, Exit Clearance, Assets Returned, or
 * already Resigned) — see EmployeeService.getAllInSeparation().
 */
@Service
public class EmployeeExitReportService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private static final float MARGIN_LEFT   = 40f;
    private static final float MARGIN_TOP    = 50f;
    private static final float MARGIN_BOTTOM = 45f;
    private static final PDRectangle A4_LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float PAGE_WIDTH    = A4_LANDSCAPE.getWidth();
    private static final float PAGE_HEIGHT   = A4_LANDSCAPE.getHeight();

    // ── PDF export ───────────────────────────────────────────────────────────

    public byte[] generatePdf(List<Employee> employees) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PdfCursor cursor = new PdfCursor(document, regular, bold);
            cursor.newPage();
            cursor.title("Employee Exit Report");
            cursor.subtitle("Generated " + LocalDateTime.now().format(TS_FMT)
                    + "  ·  " + employees.size() + " employee" + (employees.size() == 1 ? "" : "s"));
            cursor.tableHeader();

            for (Employee e : employees) {
                cursor.ensureSpace(16);
                cursor.row(e);
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
            PDPage page = new PDPage(A4_LANDSCAPE);
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
            return new float[]{0, 60, 160, 260, 350, 440, 530, 610, 690};
        }

        void tableHeader() throws IOException {
            float[] c = cols();
            String[] labels = {"EMP ID", "NAME", "DEPARTMENT", "DESIGNATION", "NOTICE START",
                    "LAST WORKING", "REASON", "CLEARANCE", "STATUS"};
            stream.setNonStrokingColor(90, 90, 90);
            for (int i = 0; i < labels.length; i++) at(labels[i], bold, 7.5f, MARGIN_LEFT + c[i]);
            stream.setNonStrokingColor(0, 0, 0);
            y -= 10;
            rule();
            y -= 4;
        }

        void row(Employee e) throws IOException {
            float[] c = cols();
            at(safe(e.getEmployeeId()), regular, 8, MARGIN_LEFT + c[0]);
            at(truncate(safe(e.getEmployeeName()), 16), regular, 8, MARGIN_LEFT + c[1]);
            at(truncate(safe(e.getDepartment()), 14), regular, 8, MARGIN_LEFT + c[2]);
            at(truncate(safe(e.getDesignation()), 13), regular, 8, MARGIN_LEFT + c[3]);
            at(safe(e.getNoticeStartDate()), regular, 8, MARGIN_LEFT + c[4]);
            at(safe(e.getLastWorkingDate()), regular, 8, MARGIN_LEFT + c[5]);
            at(truncate(safe(e.getResignationReason()), 14), regular, 8, MARGIN_LEFT + c[6]);
            at(safe(e.getExitClearanceStatus()), regular, 8, MARGIN_LEFT + c[7]);
            at(safe(e.getEmploymentStatus()), regular, 8, MARGIN_LEFT + c[8]);
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

        private static String safe(String s) { return (s == null || s.isBlank()) ? "-" : s; }
        private static String truncate(String v, int max) { return v.length() > max ? v.substring(0, max - 1) + "…" : v; }
        private static String sanitize(String text) { return text == null ? "" : text.replaceAll("[^\\x00-\\xFF]", "?"); }
    }

    // ── Excel export ─────────────────────────────────────────────────────────

    public byte[] generateExcel(List<Employee> employees) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Employee Exit Report");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"Employee ID", "Name", "Email", "Department", "Designation", "Joining Date",
                    "Notice Start Date", "Last Working Date", "Notice Period (days)", "Resignation Reason",
                    "Remarks", "Exit Clearance Status", "Clearance Completion Date", "Resigned Date", "Final Status"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Employee e : employees) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(nvl(e.getEmployeeId()));
                row.createCell(col++).setCellValue(nvl(e.getEmployeeName()));
                row.createCell(col++).setCellValue(nvl(e.getEmail()));
                row.createCell(col++).setCellValue(nvl(e.getDepartment()));
                row.createCell(col++).setCellValue(nvl(e.getDesignation()));
                row.createCell(col++).setCellValue(nvl(e.getJoiningDate()));
                row.createCell(col++).setCellValue(nvl(e.getNoticeStartDate()));
                row.createCell(col++).setCellValue(nvl(e.getLastWorkingDate()));
                row.createCell(col++).setCellValue(e.getNoticePeriodDays() != null ? e.getNoticePeriodDays() : 0);
                row.createCell(col++).setCellValue(nvl(e.getResignationReason()));
                row.createCell(col++).setCellValue(nvl(e.getSeparationRemarks()));
                row.createCell(col++).setCellValue(nvl(e.getExitClearanceStatus()));
                row.createCell(col++).setCellValue(nvl(e.getClearanceCompletionDate()));
                row.createCell(col++).setCellValue(nvl(e.getResignedDate()));
                row.createCell(col).setCellValue(nvl(e.getEmploymentStatus()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
