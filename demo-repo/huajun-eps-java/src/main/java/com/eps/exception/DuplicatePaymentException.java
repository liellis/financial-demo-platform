package com.eps.exception;

/**
 * Exception thrown when a payment is submitted with a reference that
 * already exists in the system, or when a duplicate payment is detected
 * based on matching attributes (amount, payee, date, etc.).
 *
 * <p>This maps to HTTP 409 (Conflict) via
 * {@link GlobalExceptionHandler}.</p>
 */
public class DuplicatePaymentException extends RuntimeException {

    /**
     * Constructs a new exception for a duplicate payment reference.
     *
     * @param paymentRef the payment reference that already exists
     */
    public DuplicatePaymentException(String paymentRef) {
        super("Duplicate payment detected: " + paymentRef);
    }

    /**
     * Constructs a new exception with both the new and existing references.
     *
     * @param paymentRef         the payment reference that was submitted
     * @param existingPaymentRef the existing payment reference it conflicts with
     */
    public DuplicatePaymentException(String paymentRef, String existingPaymentRef) {
        super("Duplicate payment detected: submitted ref [" + paymentRef
                + "] conflicts with existing ref [" + existingPaymentRef + "]");
    }
}
