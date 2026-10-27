package com.eps.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for the EPS REST API.
 *
 * <p>This class intercepts exceptions thrown by controller methods and
 * translates them into consistent HTTP responses with structured error
 * payloads. All error responses follow the {@link ErrorResponse} format,
 * ensuring a uniform contract for API consumers.</p>
 *
 * <p><strong>Exception to HTTP Status Mapping:</strong></p>
 * <ul>
 *   <li>{@link PaymentNotFoundException} — 404 Not Found</li>
 *   <li>{@link PaymentValidationException} — 400 Bad Request</li>
 *   <li>{@link DuplicatePaymentException} — 409 Conflict</li>
 *   <li>{@link UnauthorizedException} — 403 Forbidden</li>
 *   <li>{@link InsufficientFundsException} — 422 Unprocessable Entity</li>
 *   <li>{@link ReconciliationException} — 500 Internal Server Error</li>
 *   <li>{@link MethodArgumentNotValidException} — 400 Bad Request (field errors)</li>
 *   <li>All other exceptions — 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========================================================================
    // Structured error response record
    // ========================================================================

    /**
     * A consistent error response structure returned by all exception handlers.
     *
     * @param timestamp the time the error occurred (ISO 8601)
     * @param status    the HTTP status code
     * @param error     the HTTP status reason phrase
     * @param message   a human-readable error message
     * @param path      the request URI that triggered the error
     * @param details   optional list of detailed error messages (field errors, etc.)
     */
    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {}

    // ========================================================================
    // Exception handlers
    // ========================================================================

    /**
     * Handles {@link PaymentNotFoundException} — returns HTTP 404.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return a 404 response with the payment reference in the message
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles {@link PaymentValidationException} — returns HTTP 400
     * with the list of individual validation errors.
     *
     * @param ex      the exception containing validation error details
     * @param request the HTTP request
     * @return a 400 response with validation error messages
     */
    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentValidation(
            PaymentValidationException ex, HttpServletRequest request) {
        log.warn("Payment validation failed: {} errors", ex.getValidationErrors().size());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                new ArrayList<>(ex.getValidationErrors())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link DuplicatePaymentException} — returns HTTP 409 Conflict.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return a 409 response indicating the duplicate reference
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayment(
            DuplicatePaymentException ex, HttpServletRequest request) {
        log.warn("Duplicate payment detected: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Handles {@link UnauthorizedException} — returns HTTP 403 Forbidden.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return a 403 response
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Handles {@link InsufficientFundsException} — returns HTTP 422
     * Unprocessable Entity.
     *
     * <p>HTTP 422 is appropriate here because the request is
     * syntactically valid but cannot be processed due to a business
     * rule violation (insufficient balance).</p>
     *
     * @param ex      the exception with account and balance details
     * @param request the HTTP request
     * @return a 422 response
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of(
                        "Account: " + ex.getAccountNo(),
                        "Required: " + (ex.getRequiredAmount() != null
                                ? ex.getRequiredAmount().toPlainString() : "null"),
                        "Available: " + (ex.getAvailableBalance() != null
                                ? ex.getAvailableBalance().toPlainString() : "null")
                )
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Handles {@link ReconciliationException} — returns HTTP 500
     * Internal Server Error.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return a 500 response
     */
    @ExceptionHandler(ReconciliationException.class)
    public ResponseEntity<ErrorResponse> handleReconciliation(
            ReconciliationException ex, HttpServletRequest request) {
        log.error("Reconciliation error: {}", ex.getMessage(), ex);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getBankCode() != null
                        ? List.of("Bank: " + ex.getBankCode())
                        : null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Handles Spring's {@link MethodArgumentNotValidException} for
     * {@code @Valid} annotation failures on request bodies.
     *
     * <p>Returns HTTP 400 with field-level error details extracted
     * from the binding result.</p>
     *
     * @param ex      the exception with field errors
     * @param request the HTTP request
     * @return a 400 response with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed on request to {}: {}",
                request.getRequestURI(), ex.getMessage());

        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        // Also include global errors
        ex.getBindingResult().getGlobalErrors().stream()
                .map(ge -> ge.getObjectName() + ": " + ge.getDefaultMessage())
                .forEach(fieldErrors::add);

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for request body",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles all unhandled exceptions — returns HTTP 500
     * Internal Server Error.
     *
     * <p>This is the catch-all handler that prevents stack traces
     * from leaking to API consumers. The full exception is logged
     * server-side for debugging.</p>
     *
     * @param ex      the unexpected exception
     * @param request the HTTP request
     * @return a 500 response with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on request to {}: {}",
                request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please contact support.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
