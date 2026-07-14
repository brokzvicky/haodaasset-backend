package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.entity.Asset;
import com.vikkash.assetmanagementv1.entity.Employee;
import com.vikkash.assetmanagementv1.repository.AssetRepository;
import com.vikkash.assetmanagementv1.repository.EmployeeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import java.awt.Color;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Generates the "Employee Asset Report (PDF)" shown on the Reports page.
 * One employee per section: their details, followed by every asset
 * currently assigned to them. Employees with no assigned assets are still
 * listed, with a "No assets currently assigned" line, so the report is a
 * complete roster rather than just the employees who happen to hold gear.
 *
 * Built directly on PDFBox (no template engine) since the layout is simple
 * enough that manual positioning is easier to reason about and review than
 * a templating dependency would be.
 *
 * Requires the following Maven dependency (not bundled — add to pom.xml):
 *   <dependency>
 *     <groupId>org.apache.pdfbox</groupId>
 *     <artifactId>pdfbox</artifactId>
 *     <version>2.0.31</version>
 *   </dependency>
 */
@Service
public class ReportService {

    private static final float MARGIN_LEFT   = 50f;
    private static final float MARGIN_RIGHT  = 50f;
    private static final float MARGIN_TOP    = 60f;
    private static final float MARGIN_BOTTOM = 55f;
    private static final float PAGE_WIDTH    = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT   = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;

    public ReportService(EmployeeRepository employeeRepository, AssetRepository assetRepository) {
        this.employeeRepository = employeeRepository;
        this.assetRepository = assetRepository;
    }

    public byte[] generateEmployeeAssetReportPdf() throws IOException {
        List<Employee> employees = employeeRepository.findAll();
        employees.sort(Comparator.comparing(
                e -> e.getEmployeeName() == null ? "" : e.getEmployeeName().toLowerCase()));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            Cursor cursor = new Cursor(document, fontRegular, fontBold);
            cursor.newPage();
            cursor.writeTitle("Employee Asset Report");
            cursor.writeSubtitle("Generated " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) +
                    "  ·  " + employees.size() + " employee" + (employees.size() == 1 ? "" : "s"));
            cursor.gap(14);

            for (Employee employee : employees) {
                List<Asset> assigned = assetRepository.findByEmployeeId(employee.getEmployeeId());

                // Reserve enough vertical space for the employee header + at
                // least one row before deciding whether a page break is needed.
                cursor.ensureSpace(70);

                cursor.writeEmployeeHeader(employee, assigned.size());
                cursor.gap(4);

                if (assigned.isEmpty()) {
                    cursor.writeMuted("No assets currently assigned.");
                } else {
                    cursor.writeTableHeader();
                    for (Asset asset : assigned) {
                        cursor.ensureSpace(18);
                        cursor.writeAssetRow(asset);
                    }
                }
                cursor.gap(16);
            }

            cursor.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Tracks the current page/content stream and y-position so callers can
     * write sequential lines without manually managing pagination.
     */
    private static class Cursor {
        private final PDDocument document;
        private final PDFont fontRegular;
        private final PDFont fontBold;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument document, PDFont fontRegular, PDFont fontBold) {
            this.document = document;
            this.fontRegular = fontRegular;
            this.fontBold = fontBold;
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        /** Starts a new page if fewer than `needed` points remain above the bottom margin. */
        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN_BOTTOM) {
                newPage();
            }
        }

        void gap(float points) {
            y -= points;
        }

        void writeTitle(String text) throws IOException {
            write(text, fontBold, 18, MARGIN_LEFT);
            y -= 22;
        }

        void writeSubtitle(String text) throws IOException {
            write(text, fontRegular, 10, MARGIN_LEFT);
            y -= 14;
            drawRule();
        }

        void writeEmployeeHeader(Employee employee, int assetCount) throws IOException {
            String name = safe(employee.getEmployeeName(), "Unnamed Employee");
            String id = safe(employee.getEmployeeId(), "-");
            write(name + "  (" + id + ")", fontBold, 12, MARGIN_LEFT);
            y -= 15;

            String meta = safe(employee.getDesignation(), "-")
                    + "   ·   " + safe(employee.getDepartment(), "-")
                    + "   ·   " + safe(employee.getLocation(), "-")
                    + "   ·   " + assetCount + " asset" + (assetCount == 1 ? "" : "s");
            write(meta, fontRegular, 9, MARGIN_LEFT);
            y -= 13;
        }

        void writeMuted(String text) throws IOException {
            write(text, fontRegular, 9, MARGIN_LEFT + 10);
            y -= 13;
        }

        void writeTableHeader() throws IOException {
            float[] cols = columnPositions();
            stream.setNonStrokingColor(new Color(90, 90, 90));
            writeAt("ASSET", fontBold, 8, cols[0]);
            writeAt("BRAND / MODEL", fontBold, 8, cols[1]);
            writeAt("SERIAL NO.", fontBold, 8, cols[2]);
            writeAt("STATUS", fontBold, 8, cols[3]);
            stream.setNonStrokingColor(Color.BLACK);
            y -= 12;
            drawRule();
            y -= 2;
        }

        void writeAssetRow(Asset asset) throws IOException {
            float[] cols = columnPositions();
            String assetName   = safe(asset.getLaptopName(), safe(asset.getAssetType(), "-"));
            String brandModel  = safe(asset.getBrand(), "-") +
                    (isBlank(asset.getModel()) ? "" : " " + asset.getModel());
            String serial      = safe(asset.getSerialNumber(), "-");
            String status      = safe(asset.getAssetStatus(), "-");

            writeAt(truncate(assetName, 26), fontRegular, 9, cols[0]);
            writeAt(truncate(brandModel, 22), fontRegular, 9, cols[1]);
            writeAt(truncate(serial, 18), fontRegular, 9, cols[2]);
            writeAt(status, fontRegular, 9, cols[3]);
            y -= 15;
        }

        private float[] columnPositions() {
            return new float[]{
                    MARGIN_LEFT + 10,
                    MARGIN_LEFT + 190,
                    MARGIN_LEFT + 340,
                    MARGIN_LEFT + 460
            };
        }

        private void write(String text, PDFont font, float size, float x) throws IOException {
            writeAt(text, font, size, x);
        }

        private void writeAt(String text, PDFont font, float size, float x) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(sanitize(text));
            stream.endText();
        }

        private void drawRule() throws IOException {
            stream.setLineWidth(0.5f);
            stream.setStrokingColor(new Color(210, 210, 210));
            stream.moveTo(MARGIN_LEFT, y);
            stream.lineTo(MARGIN_LEFT + CONTENT_WIDTH, y);
            stream.stroke();
            stream.setStrokingColor(Color.BLACK);        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private static String safe(String value, String fallback) {
            return (value == null || value.isBlank()) ? fallback : value;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        private static String truncate(String value, int maxLen) {
            if (value == null) return "-";
            return value.length() > maxLen ? value.substring(0, maxLen - 3) + "..." : value;
        }

        /** PDFBox's standard fonts only support WinAnsi-encodable characters. */
        private static String sanitize(String text) {
            if (text == null) return "";
            return text.replaceAll("[^\\x00-\\xFF]", "?");
        }
    }
}
