package com.eps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for payment instruction API responses.
 * Provides a sanitized view of {@link com.eps.entity.PaymentInstruction}
 * suitable for client consumption, omitting internal fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    /** Unique payment reference */
    private String paymentRef;

    /** Batch number if part of a batch */
    private String batchNo;

    /** Source system code */
    private String sourceSystem;

    /** Payment channel */
    private String channel;

    /** Payer account (masked for non-admin users) */
    private String payerAccount;

    /** Payer name */
    private String payerName;

    /** Payee account (masked for non-admin users) */
    private String payeeAccount;

    /** Payee name */
    private String payeeName;

    /** Payee bank code */
    private String payeeBank;

    /** Payee branch code */
    private String payeeBranch;

    /** Payee BIC */
    private String payeeBic;

    /** PayNow proxy type */
    private String pynwProxyType;

    /** PayNow proxy value */
    private String pynwProxyValue;

    /** GIRO bill reference */
    private String giroBillRef;

    /** Currency code */
    private String currency;

    /** Payment amount */
    private BigDecimal amount;

    /** Remittance information */
    private String remittance;

    /** Value date */
    private LocalDate valueDate;

    /** Payment status */
    private String status;

    /** Status description (human-readable) */
    private String statusDescription;

    /** Creation timestamp */
    private LocalDateTime createTime;

    /** Creator user ID */
    private String creator;

    /** Approver user ID */
    private String approver;

    /** Approval timestamp */
    private LocalDateTime approvalTime;

    /** Bank reference (populated after submission) */
    private String bankRef;

    /** Payment network transaction ID */
    private String pynwTxnId;

    /** Actual settlement date */
    private LocalDate settleDate;

    /** Sanctions screening flag */
    private String screenFlag;

    /** Correlation ID for tracing */
    private String correlationId;
}
