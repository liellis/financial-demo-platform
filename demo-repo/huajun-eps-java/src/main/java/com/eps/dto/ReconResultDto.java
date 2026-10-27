package com.eps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for reconciliation match results.
 * Represents a single match (or mismatch) between a payment instruction
 * and a bank statement line, suitable for display in the recon dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconResultDto {

    /** Unique match identifier */
    private String matchId;

    /** Payment reference from EPS side */
    private String paymentRef;

    /** Statement entry reference from bank side */
    private String entryRef;

    /** Statement line number */
    private Integer stmtLineNo;

    /** Match type: AT (automatic) or MN (manual) */
    private String matchType;

    /** Match type description */
    private String matchTypeDescription;

    /** Match status: MA, BO, EO, AM */
    private String matchStatus;

    /** Match status description */
    private String matchStatusDescription;

    /** Payment amount */
    private BigDecimal paymentAmount;

    /** Statement line amount */
    private BigDecimal statementAmount;

    /** Absolute difference between payment and statement amounts */
    private BigDecimal amtDiff;

    /** Match timestamp */
    private LocalDateTime matchTime;

    /** Operator user ID (for manual matches) */
    private String operator;

    /** Auto-match confidence score (0.0 to 1.0) */
    private BigDecimal confidence;

    /** Direction indicator */
    private String direction;

    /** Payment currency */
    private String paymentCurrency;

    /** Statement currency */
    private String statementCurrency;

    /** Counterparty name from statement */
    private String counterpartyName;
}
