package com.vikkash.assetmanagementv1.exception;

/**
 * Thrown when an uploaded invoice PDF cannot be processed for auto-fill
 * (unreadable file, wrong file type, corrupted PDF, etc). Always carries a
 * user-friendly message — never affects/rolls back any billing record,
 * since extraction happens before anything is saved.
 */
public class InvoiceExtractionException extends RuntimeException {
    public InvoiceExtractionException(String message) {
        super(message);
    }
}
