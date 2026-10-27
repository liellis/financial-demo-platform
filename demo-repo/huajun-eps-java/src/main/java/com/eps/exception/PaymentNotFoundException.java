package com.eps.exception;

/**
 * Exception thrown when a payment instruction cannot be found by its
 * reference identifier in the EPS system.
 *
 * <p>This is typically thrown by service-layer methods when a
 * repository lookup returns an empty result. It maps to an HTTP
 * 404 (Not Found) status in the REST API via
 * {@link GlobalExceptionHandler}.</p>
 */
public class PaymentNotFoundException extends RuntimeException {

    /**
     * Constructs a new exception for a missing payment reference.
     *
     * @param paymentRef the payment reference that was not found
     */
    public PaymentNotFoundException(String paymentRef) {
        super("Payment not found: " + paymentRef);
    }

    /**
     * Constructs a new exception with an underlying cause.
     *
     * @param paymentRef the payment reference that was not found
     * @param cause      the underlying exception that triggered this lookup failure
     */
    public PaymentNotFoundException(String paymentRef, Throwable cause) {
        super("Payment not found: " + paymentRef, cause);
    }
}
