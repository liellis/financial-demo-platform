package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reconciliation match entity, mapped to table EPSRECN.
 * Records the result of matching a bank statement line against
 * an internal payment instruction, including any differences
 * and the user or process that performed the match.
 */
@Entity
@Table(name = "EPSRECN")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconMatch {

    /** Unique match identifier, length 20 */
    @Id
    @Column(name = "MATCH_ID", length = 20, nullable = false)
    private String matchId;

    /** Statement identifier from the bank file, length 20 */
    @Column(name = "STATEMENT_NO", length = 20)
    private String statementNo;

    /** Bank reference for this entry, length 20 */
    @Column(name = "ENTRY_REF", length = 20)
    private String entryRef;

    /** Sequential line number within the statement entry */
    @Column(name = "LINE_NO")
    private Integer lineNo;

    /** Reference to the matched payment instruction, length 20 */
    @Column(name = "PAYMENT_REF", length = 20)
    private String paymentRef;

    /** Payment amount from the internal system */
    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    /** Statement amount from the bank record */
    @Column(name = "STMT_AMOUNT", precision = 18, scale = 2)
    private BigDecimal stmtAmount;

    /** Difference between payment amount and statement amount */
    @Column(name = "DIFFERENCE", precision = 18, scale = 2)
    private BigDecimal difference;

    /** Date when the match was performed */
    @Column(name = "MATCH_DATE")
    private LocalDate matchDate;

    /**
     * Status of the match.
     * MA = Matched, BO = Break, EO = Extra-only, AM = Amount-mismatch.
     */
    @Column(name = "MATCH_STATUS", length = 2)
    private String matchStatus;

    /** User ID or system process that performed the match, length 20 */
    @Column(name = "MATCHED_BY", length = 20)
    private String matchedBy;

    /** Free-text comments or notes about the match, length 200 */
    @Column(name = "COMMENTS", length = 200)
    private String comments;
}
