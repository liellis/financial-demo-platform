package com.eps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for creating a new payment instruction.
 * Validated at the controller layer before being mapped to the
 * {@link com.eps.entity.PaymentInstruction} and {@link com.eps.entity.PaymentDetail} entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    /** Payment channel: FAST, MEPS, PAYNOW, GIRO, SWIFT */
    @NotBlank(message = "Channel is required")
    @Size(max = 10, message = "Channel must not exceed 10 characters")
    private String channel;

    /** Originating system code */
    @Size(max = 10, message = "Source system must not exceed 10 characters")
    private String sourceSystem;

    /** Payer account number */
    @NotBlank(message = "Payer account is required")
    @Size(max = 30, message = "Payer account must not exceed 30 characters")
    private String payerAccount;

    /** Payer name */
    @Size(max = 60, message = "Payer name must not exceed 60 characters")
    private String payerName;

    /** Payee account number */
    @Size(max = 30, message = "Payee account must not exceed 30 characters")
    private String payeeAccount;

    /** Payee name */
    @Size(max = 60, message = "Payee name must not exceed 60 characters")
    private String payeeName;

    /** Payee bank code */
    @Size(max = 10, message = "Payee bank code must not exceed 10 characters")
    private String payeeBank;

    /** Payee branch code */
    @Size(max = 10, message = "Payee branch code must not exceed 10 characters")
    private String payeeBranch;

    /** Payee BIC/SWIFT code */
    @Size(max = 11, message = "Payee BIC must not exceed 11 characters")
    private String payeeBic;

    /** PayNow proxy type (NRIC, MOBILE, etc.) */
    @Size(max = 2, message = "PayNow proxy type must not exceed 2 characters")
    private String pynwProxyType;

    /** PayNow proxy value */
    @Size(max = 30, message = "PayNow proxy value must not exceed 30 characters")
    private String pynwProxyValue;

    /** GIRO bill reference */
    @Size(max = 50, message = "GIRO bill reference must not exceed 50 characters")
    private String giroBillRef;

    /** ISO 4217 currency code */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    /** Payment amount (must be positive) */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 16, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    /** Remittance information */
    @Size(max = 140, message = "Remittance must not exceed 140 characters")
    private String remittance;

    /** Requested value date */
    @FutureOrPresent(message = "Value date must be today or in the future")
    private LocalDate valueDate;

    /** Payment detail lines (optional invoice-level breakdown) */
    private List<PaymentDetailLine> details;

    // -- Inner class for detail lines -- //

    /**
     * A single detail line within a payment request, representing
     * an individual invoice or cost-center allocation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetailLine {

        /** Invoice reference */
        @Size(max = 50)
        private String invoiceRef;

        /** Cost center for accounting */
        @Size(max = 20)
        private String costCenter;

        /** Tax amount for this line */
        @Digits(integer = 16, fraction = 2)
        private BigDecimal taxAmount;

        /** Line-item description */
        @Size(max = 200)
        private String description;
    }
}
