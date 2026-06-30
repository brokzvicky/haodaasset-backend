package com.vikkash.assetmanagementv1.exception;

/** Thrown when the SMTP send for an OTP (or any transactional) email fails. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
