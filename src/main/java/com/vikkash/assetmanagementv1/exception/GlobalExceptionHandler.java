package com.vikkash.assetmanagementv1.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception → HTTP response mapping.
 * Every exception type produces the same ApiError JSON shape so the frontend
 * can rely on a consistent error structure regardless of what went wrong.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest req) {
        // Don't log at WARN/ERROR — wrong credentials are an expected user event, not a system failure
        log.debug("Auth failure at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.debug("Resource not found at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        log.debug("Duplicate resource at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(InvoiceExtractionException.class)
    public ResponseEntity<ApiError> handleInvoiceExtraction(InvoiceExtractionException ex, HttpServletRequest req) {
        // Expected/user-facing (bad or unreadable PDF) — not a system failure, so no ERROR-level noise.
        log.debug("Invoice extraction failed at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), req);
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ApiError> handleOtp(OtpException ex, HttpServletRequest req) {
        log.debug("OTP failure at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiError> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest req) {
        log.error("Email delivery failure at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        log.debug("Upload too large at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "The uploaded file is too large. Maximum size is 10MB.", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.debug("Bad request at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    /**
     * Credential decrypt failures (CredentialEncryptionUtil.decrypt). This is a
     * data-state problem, not a transient server fault — the ciphertext either no
     * longer matches the configured encryption key, or is corrupted/invalid. A
     * retry will never succeed on its own; the admin must re-enter the password
     * (PUT /api/network/{id}) to re-encrypt it under the current key. Returned as
     * 422 (not 500) so the client can distinguish "this row needs fixing" from an
     * actual server error, and the full stack trace (with the real JCE exception,
     * e.g. AEADBadTagException) is logged server-side for triage.
     */
    @ExceptionHandler(CredentialDecryptionException.class)
    public ResponseEntity<ApiError> handleCredentialDecryption(CredentialDecryptionException ex,
                                                                 HttpServletRequest req) {
        log.error("Credential decryption failed at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), req);
    }

    /**
     * Any other "this object is in an invalid state to complete this operation"
     * failure. Logged at ERROR with the full stack trace so the real cause is
     * visible server-side, while the client only sees a safe generic message.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.error("Illegal state at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Unable to process this credential. Please contact an administrator.", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ApiError body = new ApiError(
                HttpStatus.BAD_REQUEST.value(), "Validation Failed",
                "One or more fields are invalid", req.getRequestURI());
        body.setFieldErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied at {} by principal: {}", req.getRequestURI(), req.getUserPrincipal());
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource.", req);
    }

    /**
     * Catch-all for unhandled exceptions. Logged at ERROR so these are
     * immediately visible in production logs without exposing internals to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again.", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error,
                                           String message, HttpServletRequest req) {
        ApiError body = new ApiError(status.value(), error, message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}