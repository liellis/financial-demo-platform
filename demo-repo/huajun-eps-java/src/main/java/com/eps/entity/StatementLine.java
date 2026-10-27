package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bank statement line entity, mapped to table EPSSTMT.
 * Each record represents one line item from a bank statement file
 * that has been parsed and loaded into the system for reconciliation
 * against internal payment records.
 *
 * Uses {@link StatementLineId} as the composite primary key.
 */
@Entity
@Table(name = "EPSSTMT")
@IdClass(StatementLineId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementLine {

    /** Statement identifier from the bank file — part of composite key */
    @Id
    @Column(name = "STATEMENT_NO", length = 20, nullable = false)
    private String statementNo;

    /** Bank reference for this entry — part of composite key */
    @Id
    @Column(name = "ENTRY_REF", length = 20, nullable = false)
    private String entryRef;

    /** Sequential line number within the statement entry — part of composite key */
    @Id
    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    /** Bank code identifying the source bank, length 10 */
    @Column(name = "BANK_CODE", length = 10)
    private String bankCode;

    /** Account number as it appears on the statement, length 30 */
    @Column(name = "ACCOUNT_NO", length = 30)
    private String accountNo;

    /** Value date of the transaction on the bank statement */
    @Column(name = "VALUE_DATE")
    private LocalDate valueDate;

    /** Entry date when the transaction was posted by the bank */
    @Column(name = "ENTRY_DATE")
    private LocalDate entryDate;

    /** ISO 4217 currency code, length 3 (e.g. SGD, USD) */
    @Column(name = "CURRENCY", length = 3)
    private String currency;

    /** Transaction amount in the statement currency */
    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    /** Bank transaction reference number, length 40 */
    @Column(name = "TRANSACTION_REF", length = 40)
    private String transactionRef;

    /** Free-text narrative or description from the bank statement, length 200 */
    @Column(name = "NARRATIVE", length = 200)
    private String narrative;

    /**
     * Reconciliation status of this statement line.
     * MA = Matched, BO = Break, EO = Extra-only, AM = Amount-mismatch.
     */
    @Column(name = "RECON_STATUS", length = 2)
    private String reconStatus;

    /** Date when reconciliation was last performed on this line */
    @Column(name = "RECON_DATE")
    private LocalDate reconDate;

    /** Reference to the matched payment instruction, length 20 */
    @Column(name = "MATCHED_PAYMENT_REF", length = 20)
    private String matchedPaymentRef;
}
