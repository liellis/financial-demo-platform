package com.eps.exception;

/**
 * Exception thrown when a reconciliation process encounters an error,
 * such as a mismatch between internal payment records and bank statement
 * entries, or a system-level failure during reconciliation batch processing.
 *
 * <p>This maps to HTTP 500 (Internal Server Error) via
 * {@link GlobalExceptionHandler}.</p>
 */
public class ReconciliationException extends RuntimeException {

    /** The bank code associated with the reconciliation error, if applicable. */
    private final String bankCode;

    /**
     * Constructs a new exception with a descriptive message.
     *
     * @param message the error description
     */
    public ReconciliationException(String message) {
        super(message);
        this.bankCode = null;
    }

    /**
     * Constructs a new exception with a message and underlying cause.
     *
     * @param message the error description
     * @param cause   the underlying exception
     */
    public ReconciliationException(String message, Throwable cause) {
        super(message, cause);
        this.bankCode = null;
    }

    /**
     * Constructs a new exception associated with a specific bank code.
     *
     * @param bankCode the bank code (e.g. "DBSSGSG") for which reconciliation failed
     * @param message  the error description
     */
    public ReconciliationException(String bankCode, String message) {
        super("Reconciliation failed for bank [" + bankCode + "]: " + message);
        this.bankCode = bankCode;
    }

    /**
     * Returns the bank code associated with this reconciliation error,
     * or null if no specific bank is associated.
     *
     * @return the bank code, or null
     */
    public String getBankCode() {
        return bankCode;
    }
}
