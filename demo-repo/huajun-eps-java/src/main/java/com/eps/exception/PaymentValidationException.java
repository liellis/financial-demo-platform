package com.eps.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when payment instruction data fails validation
 * (field-level or business-rule validation).
 *
 * <p>This exception carries a list of individual validation error
 * messages, enabling the API to return detailed, field-specific
 * error feedback to the caller. It maps to HTTP 400 (Bad Request)
 * via {@link GlobalExceptionHandler}.</p>
 */
public class PaymentValidationException extends RuntimeException {

    /** The list of individual validation error messages. */
    private final List<String> validationErrors;

    /**
     * Constructs a new exception with a single validation message.
     *
     * @param message the validation error description
     */
    public PaymentValidationException(String message) {
        super(message);
        this.validationErrors = Collections.singletonList(message);
    }

    /**
     * Constructs a new exception with a message and underlying cause.
     *
     * @param message the validation error description
     * @param cause   the underlying exception
     */
    public PaymentValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationErrors = Collections.singletonList(message);
    }

    /**
     * Constructs a new exception with a list of validation errors.
     *
     * @param validationErrors the list of individual validation error messages;
     *                         must not be null; if empty, a default message is used
     */
    public PaymentValidationException(List<String> validationErrors) {
        super(buildMessage(validationErrors));
        this.validationErrors = Collections.unmodifiableList(
                new ArrayList<>(validationErrors != null ? validationErrors : List.of())
        );
    }

    /**
     * Returns the list of individual validation error messages.
     *
     * @return an unmodifiable list of validation error strings
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Builds a composite message from a list of errors.
     *
     * @param errors the list of error messages
     * @return a concatenated message string
     */
    private static String buildMessage(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Payment validation failed";
        }
        if (errors.size() == 1) {
            return errors.get(0);
        }
        return "Payment validation failed with " + errors.size() + " errors";
    }
}
