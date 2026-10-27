package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payment instruction header entity, mapped to table EPSPOH.
 * Represents a single payment order submitted through the EPS system,
 * supporting multiple channels (FAST, MEPS, PAYNOW, GIRO, SWIFT) and
 * a full lifecycle from creation through approval, submission, and settlement.
 */
@Entity
@Table(name = "EPSPOH")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInstruction {

    /** Unique payment reference identifier, max 20 characters */
    @Id
    @Column(name = "PAYMENT_REF", length = 20, nullable = false)
    private String paymentRef;

    /** Batch processing number grouping related payments, length 20 */
    @Column(name = "BATCH_NO", length = 20)
    private String batchNo;

    /** Originating system code, e.g. ERP, HR, length 10 */
    @Column(name = "SOURCE_SYSTEM", length = 10)
    private String sourceSystem;

    /** Payment channel: FAST, MEPS, PAYNOW, GIRO, SWIFT */
    @Column(name = "CHANNEL", length = 10)
    private String channel;

    /** Payer (debit) account number, length 30 */
    @Column(name = "PAYER_ACCOUNT", length = 30)
    private String payerAccount;

    /** Payer name as registered with the bank, length 60 */
    @Column(name = "PAYER_NAME", length = 60)
    private String payerName;

    /** Payee (credit) account number, length 30 */
    @Column(name = "PAYEE_ACCOUNT", length = 30)
    private String payeeAccount;

    /** Payee name as registered with the recipient bank, length 60 */
    @Column(name = "PAYEE_NAME", length = 60)
    private String payeeName;

    /** Payee bank code, length 10 */
    @Column(name = "PAYEE_BANK", length = 10)
    private String payeeBank;

    /** Payee branch code, length 10 */
    @Column(name = "PAYEE_BRANCH", length = 10)
    private String payeeBranch;

    /** Payee BIC/SWIFT code, length 11 */
    @Column(name = "PAYEE_BIC", length = 11)
    private String payeeBic;

    /** PayNow proxy type (NRIC, MOBILE, etc.), length 2 */
    @Column(name = "PYNW_PROXY_TYPE", length = 2)
    private String pynwProxyType;

    /** PayNow proxy value (the actual NRIC or mobile number), length 30 */
    @Column(name = "PYNW_PROXY_VALUE", length = 30)
    private String pynwProxyValue;

    /** GIRO bill reference number, length 50 */
    @Column(name = "GIRO_BILL_REF", length = 50)
    private String giroBillRef;

    /** ISO 4217 currency code, length 3 (e.g. SGD, USD) */
    @Column(name = "CURRENCY", length = 3)
    private String currency;

    /** Payment amount in the stated currency */
    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    /** Remittance information / payment narrative, length 140 */
    @Column(name = "REMITTANCE", length = 140)
    private String remittance;

    /** Requested value date for the payment */
    @Column(name = "VALUE_DATE")
    private LocalDate valueDate;

    /**
     * Current status of the payment instruction.
     * Enum values: INIT, APPR, WAIT, SENT, ACK, SETT, RJCT, CANC.
     */
    @Column(name = "STATUS", length = 4)
    private String status;

    /** Timestamp when the instruction was created */
    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    /** User ID of the approver (maker-checker), length 20 */
    @Column(name = "APPROVER", length = 20)
    private String approver;

    /** Timestamp when approval was granted */
    @Column(name = "APPROVAL_TIME")
    private LocalDateTime approvalTime;

    /** Bank-generated reference after submission, length 40 */
    @Column(name = "BANK_REF", length = 40)
    private String bankRef;

    /** Payment network transaction identifier, length 40 */
    @Column(name = "PYNW_TXN_ID", length = 40)
    private String pynwTxnId;

    /** Actual settlement date reported by the bank/network */
    @Column(name = "SETTLE_DATE")
    private LocalDate settleDate;

    /** User ID who created the instruction, length 20 */
    @Column(name = "CREATOR", length = 20)
    private String creator;

    /**
     * Sanctions screening flag.
     * CL = cleared, PM = pending manual review, HT = hit/match found.
     */
    @Column(name = "SCREEN_FLAG", length = 2)
    private String screenFlag;

    /** Correlation identifier for end-to-end tracing across systems, length 40 */
    @Column(name = "CORRELATION_ID", length = 40)
    private String correlationId;
}
