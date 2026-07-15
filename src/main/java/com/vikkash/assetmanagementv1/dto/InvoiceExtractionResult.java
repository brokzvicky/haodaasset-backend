package com.vikkash.assetmanagementv1.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of auto-reading an uploaded invoice PDF (Service Billing → Invoice
 * PDF Auto-Fill). Every field is nullable — only what could be confidently
 * identified from the invoice is populated; everything else stays null so
 * the frontend leaves it blank for the admin to fill in manually.
 *
 * This is a preview-only payload: producing it never creates or updates any
 * {@code ServiceBilling} record. The admin still reviews and saves.
 */
public class InvoiceExtractionResult {

    private String vendorName;
    private String serviceProvider;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate billingFromDate;
    private LocalDate billingToDate;
    private BigDecimal amount;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDate dueDate;
    private String invoiceReference;
    private String description;

    /** True if the PDF had no extractable text layer and OCR was used instead. */
    private boolean ocrUsed;

    /** True if any usable text at all (typed or OCR'd) was found in the document. */
    private boolean textFound;

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = serviceProvider; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public LocalDate getBillingFromDate() { return billingFromDate; }
    public void setBillingFromDate(LocalDate billingFromDate) { this.billingFromDate = billingFromDate; }

    public LocalDate getBillingToDate() { return billingToDate; }
    public void setBillingToDate(LocalDate billingToDate) { this.billingToDate = billingToDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getInvoiceReference() { return invoiceReference; }
    public void setInvoiceReference(String invoiceReference) { this.invoiceReference = invoiceReference; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isOcrUsed() { return ocrUsed; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }

    public boolean isTextFound() { return textFound; }
    public void setTextFound(boolean textFound) { this.textFound = textFound; }
}
