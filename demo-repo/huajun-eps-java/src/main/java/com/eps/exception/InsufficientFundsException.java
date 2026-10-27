package com.eps.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a payment cannot be processed because the
 * source account has insufficient funds to cover the payment amount.
 *
 * <p>This maps to HTTP 422 (Unprocessable Entity) via
 * {@link GlobalExceptionHandler}.</p>
 */
public class InsufficientFundsException extends RuntimeException {

    /** The account number with insufficient funds. */
    private final String accountNo;

    /** The amount required to process the payment. */
    private final BigDecimal requiredAmount;

    /** The available balance in the account. */
    private final BigDecimal availableBalance;

    /**
     * Constructs a new exception with account details.
     *
     * @param accountNo        the source account number
     * @param requiredAmount   the amount required for the payment
     * @param availableBalance the actual balance available
     */
    public InsufficientFundsException(String accountNo, BigDecimal requiredAmount,
                                       BigDecimal availableBalance) {
        super(buildMessage(accountNo, requiredAmount, availableBalance));
        this.accountNo = accountNo;
        this.requiredAmount = requiredAmount;
        this.availableBalance = availableBalance;
    }

    /**
     * Returns the account number with insufficient funds.
     *
     * @return the account number
     */
    public String getAccountNo() {
        return accountNo;
    }

    /**
     * Returns the amount required for the payment.
     *
     * @return the required amount
     */
    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    /**
     * Returns the available balance in the account.
     *
     * @return the available balance
     */
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    /**
     * Returns the shortfall (required minus available).
     *
     * @return the shortfall amount, or null if either amount is null
     */
    public BigDecimal getShortfall() {
        if (requiredAmount != null && availableBalance != null) {
            return requiredAmount.subtract(availableBalance);
        }
        return null;
    }

    /**
     * Builds a human-readable message from the account details.
     *
     * @param accountNo   the account number
     * @param required    the required amount
     * @param available   the available balance
     * @return the formatted message
     */
    private static String buildMessage(String accountNo, BigDecimal required,
                                        BigDecimal available) {
        return String.format(
                "Insufficient funds in account [%s]: required %s, available %s",
                accountNo,
                required != null ? required.toPlainString() : "null",
                available != null ? available.toPlainString() : "null"
        );
    }
}
