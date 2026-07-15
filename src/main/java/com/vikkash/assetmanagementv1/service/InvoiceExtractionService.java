package com.vikkash.assetmanagementv1.service;

import com.vikkash.assetmanagementv1.dto.InvoiceExtractionResult;
import com.vikkash.assetmanagementv1.exception.InvoiceExtractionException;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an uploaded invoice PDF (Service Billing → "Invoice PDF Auto-Fill")
 * and returns only the fields it can confidently identify. Never writes
 * anything to the database — this is a stateless read-only preview step
 * that runs before the admin reviews and saves the form.
 *
 * Pipeline:
 *  1. Try PDFBox's text layer extraction (fast, for text-based/"born-digital" PDFs).
 *  2. If that yields little/no text, the PDF is likely a scanned image —
 *     render each page to an image and run Tesseract OCR over it instead.
 *  3. Run a set of label-anchored regexes over whatever text was found.
 *     Fields are only filled when a recognizable label ("Invoice No:",
 *     "GST Amount:", etc) is present next to the value — we deliberately
 *     avoid "smart guessing" free-floating numbers/names, since a wrong
 *     guess is worse than an empty field the admin fills in themselves.
 *
 * Requires the following Maven dependencies (PDFBox is already used
 * elsewhere in this project for report generation — Tess4j is new and
 * needed only for OCR of scanned invoices; if it isn't on the classpath,
 * text-based PDFs still work fine and scanned ones simply fall back to
 * empty fields instead of throwing):
 *   <dependency>
 *     <groupId>net.sourceforge.tess4j</groupId>
 *     <artifactId>tess4j</artifactId>
 *     <version>5.11.0</version>
 *   </dependency>
 * Tess4j also needs the native Tesseract engine + trained language data
 * available on the server (or bundled tessdata pointed to via the
 * `app.ocr.tessdata-path` property) — see application.properties.
 */
@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);

    /** Below this many characters of extracted text, we treat the PDF as scanned/image-only and try OCR. */
    private static final int MIN_TEXT_LENGTH = 25;

    /** Cap OCR to the first few pages for speed — invoices rarely need more. */
    private static final int MAX_OCR_PAGES = 5;

    @Value("${app.ocr.tessdata-path:}")
    private String tessdataPath;

    // ── Public entry point ──────────────────────────────────────────────────

    public InvoiceExtractionResult extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvoiceExtractionException("Please choose a PDF invoice to upload.");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        boolean isPdf = "application/pdf".equalsIgnoreCase(file.getContentType()) || name.endsWith(".pdf");
        if (!isPdf) {
            throw new InvoiceExtractionException("Only PDF files are supported for invoice auto-fill.");
        }

        String text;
        boolean ocrUsed = false;
        try (PDDocument doc = PDDocument.load(file.getBytes())) {
            text = new PDFTextStripper().getText(doc);

            if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
                // No usable text layer — likely a scanned invoice. Try OCR, but
                // never fail the whole request if OCR itself isn't available;
                // the admin can still fill everything in manually.
                try {
                    String ocrText = ocrDocument(doc);
                    if (ocrText != null && !ocrText.isBlank()) {
                        text = ocrText;
                        ocrUsed = true;
                    }
                } catch (Throwable ocrFailure) {
                    log.warn("OCR fallback unavailable or failed for '{}': {}", name, ocrFailure.toString());
                }
            }
        } catch (InvoiceExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read uploaded invoice PDF '{}': {}", name, e.getMessage(), e);
            throw new InvoiceExtractionException(
                    "We couldn't read this invoice PDF. Please check the file and try again, or fill in the details manually.");
        }

        InvoiceExtractionResult result = parse(text == null ? "" : text);
        result.setOcrUsed(ocrUsed);
        result.setTextFound(text != null && !text.isBlank());
        return result;
    }

    private String ocrDocument(PDDocument doc) throws Exception {
        Tesseract tesseract = new Tesseract();
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            tesseract.setDatapath(tessdataPath);
        }
        PDFRenderer renderer = new PDFRenderer(doc);
        StringBuilder sb = new StringBuilder();
        int pages = Math.min(doc.getNumberOfPages(), MAX_OCR_PAGES);
        for (int i = 0; i < pages; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 200);
            sb.append(tesseract.doOCR(image)).append('\n');
        }
        return sb.toString();
    }

    // ── Field parsing ────────────────────────────────────────────────────

    private InvoiceExtractionResult parse(String rawText) {
        String text = rawText.replace("\r", "\n");
        InvoiceExtractionResult r = new InvoiceExtractionResult();

        r.setInvoiceNumber(firstGroup(text, INVOICE_NUMBER_PATTERNS));
        r.setInvoiceReference(firstGroup(text, REFERENCE_PATTERNS));
        r.setVendorName(cleanName(firstGroup(text, VENDOR_PATTERNS)));
        r.setServiceProvider(cleanName(firstGroup(text, SERVICE_PROVIDER_PATTERNS)));
        r.setCurrency(detectCurrency(text));
        r.setDescription(cleanName(firstGroup(text, DESCRIPTION_PATTERNS)));

        r.setInvoiceDate(firstDate(text, INVOICE_DATE_PATTERNS));
        r.setDueDate(firstDate(text, DUE_DATE_PATTERNS));

        Matcher period = PERIOD_PATTERN.matcher(text);
        if (period.find()) {
            r.setBillingFromDate(parseDate(period.group(1)));
            r.setBillingToDate(parseDate(period.group(2)));
        }

        r.setTotalAmount(firstAmount(text, TOTAL_PATTERNS));
        r.setGstAmount(firstAmount(text, GST_PATTERNS));
        r.setAmount(firstAmount(text, AMOUNT_PATTERNS));

        return r;
    }

    // ── Label-anchored patterns ──────────────────────────────────────────
    // Deliberately conservative: only match when a recognizable label
    // precedes the value, so we never auto-fill a confident-looking guess.

    private static final String DATE_TOKEN =
            "\\d{1,2}[\\-/\\s][A-Za-z]{3,9}[\\-/\\s]\\d{2,4}|\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2}";

    private static final List<Pattern> INVOICE_NUMBER_PATTERNS = List.of(
            p("(?:Invoice|Tax Invoice)\\s*(?:No\\.?|Number|#)\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)"),
            p("Inv(?:oice)?\\.?\\s*No\\.?\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)"),
            p("Bill\\s*No\\.?\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)")
    );

    private static final List<Pattern> REFERENCE_PATTERNS = List.of(
            p("(?:Invoice\\s*)?Reference\\s*(?:No\\.?|Number|ID)?\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)"),
            p("Ref\\.?\\s*No\\.?\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)"),
            p("PO\\s*(?:No\\.?|Number)\\s*[:\\-]\\s*([A-Za-z0-9\\-\\/_]+)")
    );

    private static final List<Pattern> VENDOR_PATTERNS = List.of(
            p("Vendor\\s*(?:Name)?\\s*[:\\-]\\s*([^\\n]{2,80})"),
            p("Billed\\s*By\\s*[:\\-]\\s*([^\\n]{2,80})"),
            p("Seller\\s*(?:Name)?\\s*[:\\-]\\s*([^\\n]{2,80})"),
            p("From\\s*[:\\-]\\s*([^\\n]{2,80})")
    );

    private static final List<Pattern> SERVICE_PROVIDER_PATTERNS = List.of(
            p("Service\\s*Provider\\s*[:\\-]\\s*([^\\n]{2,80})"),
            p("Provider\\s*(?:Name)?\\s*[:\\-]\\s*([^\\n]{2,80})"),
            p("Company\\s*Name\\s*[:\\-]\\s*([^\\n]{2,80})")
    );

    private static final List<Pattern> DESCRIPTION_PATTERNS = List.of(
            p("(?:Service\\s*Details|Description\\s*of\\s*Services?|Description)\\s*[:\\-]\\s*([^\\n]{2,200})"),
            p("Particulars\\s*[:\\-]\\s*([^\\n]{2,200})")
    );

    private static final List<Pattern> INVOICE_DATE_PATTERNS = List.of(
            p("(?:Invoice\\s*Date|Date\\s*of\\s*Invoice|Bill\\s*Date)\\s*[:\\-]\\s*(" + DATE_TOKEN + ")")
    );

    private static final List<Pattern> DUE_DATE_PATTERNS = List.of(
            p("(?:Due\\s*Date|Payment\\s*Due(?:\\s*Date)?)\\s*[:\\-]\\s*(" + DATE_TOKEN + ")")
    );

    private static final Pattern PERIOD_PATTERN = p(
            "(?:Billing|Service)\\s*(?:Period|Cycle)\\s*[:\\-]?\\s*(" + DATE_TOKEN + ")\\s*(?:to|-|–|through|~)\\s*(" + DATE_TOKEN + ")"
    );

    private static final List<Pattern> TOTAL_PATTERNS = List.of(
            p("Grand\\s*Total\\s*[:\\-]?\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)"),
            p("Total\\s*Amount\\s*(?:Due|Payable)?\\s*[:\\-]?\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)"),
            p("(?<!Sub[\\s-])\\bTotal\\s*[:\\-]\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)")
    );

    private static final List<Pattern> GST_PATTERNS = List.of(
            p("(?:GST|IGST|Tax)\\s*Amount\\s*[:\\-]?\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)"),
            p("Total\\s*(?:GST|Tax)\\s*[:\\-]?\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)"),
            p("\\bGST\\s*(?:@\\s*[0-9.]+%)?\\s*[:\\-]\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)")
    );

    private static final List<Pattern> AMOUNT_PATTERNS = List.of(
            p("(?:Sub\\s*-?\\s*Total|Taxable\\s*(?:Value|Amount))\\s*[:\\-]?\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)"),
            p("\\bAmount\\s*[:\\-]\\s*([₹$€£]?\\s*[0-9][0-9,]*\\.?[0-9]*)")
    );

    private static final Pattern[] CURRENCY_HINTS = {
            Pattern.compile("₹|\\bINR\\b|\\bRs\\.?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$|\\bUSD\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("€|\\bEUR\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("£|\\bGBP\\b", Pattern.CASE_INSENSITIVE),
    };
    private static final String[] CURRENCY_CODES = { "INR", "USD", "EUR", "GBP" };

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    // ── Extraction helpers ───────────────────────────────────────────────

    private static String firstGroup(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String value = m.group(1).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private static String cleanName(String value) {
        if (value == null) return null;
        // Trim trailing punctuation/whitespace and collapse internal whitespace picked up across line wraps.
        String cleaned = value.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("[,;:]+$", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static LocalDate firstDate(String text, List<Pattern> patterns) {
        String raw = firstGroup(text, patterns);
        return raw == null ? null : parseDate(raw);
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"), DateTimeFormatter.ofPattern("d MMMM yyyy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy"), DateTimeFormatter.ofPattern("d-MMMM-yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy"), DateTimeFormatter.ofPattern("MMMM d, yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("d-M-yy"),
    };

    private static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, fmt.withLocale(Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static BigDecimal firstAmount(String text, List<Pattern> patterns) {
        String raw = firstGroup(text, patterns);
        if (raw == null) return null;
        String digits = raw.replaceAll("[₹$€£,\\s]", "");
        if (digits.isEmpty()) return null;
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String detectCurrency(String text) {
        for (int i = 0; i < CURRENCY_HINTS.length; i++) {
            if (CURRENCY_HINTS[i].matcher(text).find()) {
                return CURRENCY_CODES[i];
            }
        }
        return null;
    }
}
