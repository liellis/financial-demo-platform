package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Internal account entity, mapped to table EPSACT.
 * Represents a bank account held by the organization that is
 * tracked within the EPS system. Each account is linked to a
 * specific bank, branch, and currency.
 */
@Entity
@Table(name = "EPSACT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalAccount {

    /** Unique account number, length 30 */
    @Id
    @Column(name = "ACCOUNT_NO", length = 30, nullable = false)
    private String accountNo;

    /** Human-readable account name or description, length 60 */
    @Column(name = "ACCOUNT_NAME", length = 60)
    private String accountName;

    /** Bank code identifying the financial institution, length 10 */
    @Column(name = "BANK_CODE", length = 10)
    private String bankCode;

    /** Branch code within the bank, length 10 */
    @Column(name = "BRANCH_CODE", length = 10)
    private String branchCode;

    /** ISO 4217 currency code, length 3 (e.g. SGD, USD) */
    @Column(name = "CURRENCY", length = 3)
    private String currency;

    /** Current book balance of the account */
    @Column(name = "CURRENT_BALANCE", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    /** Available balance (current balance minus holds), for payment decisions */
    @Column(name = "AVAILABLE_BALANCE", precision = 18, scale = 2)
    private BigDecimal availableBalance;

    /** Timestamp of the last balance update */
    @Column(name = "LAST_UPDATE_TIME")
    private LocalDateTime lastUpdateTime;

    /**
     * Account status.
     * A = Active, D = Dormant, C = Closed.
     * See {@code AccountStatus} enum for allowed values.
     */
    @Column(name = "STATUS", length = 1)
    private String status;

    /** BSB (Bank State Branch) number for Australian routing, length 15 */
    @Column(name = "BSB_NUMBER", length = 15)
    private String bsbNumber;
}
