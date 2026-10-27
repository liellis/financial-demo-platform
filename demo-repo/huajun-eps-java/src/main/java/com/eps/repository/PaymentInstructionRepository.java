package com.eps.repository;

import com.eps.entity.PaymentInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PaymentInstruction} entity.
 * <p>
 * Provides query methods for payment lifecycle management, daily
 * position monitoring, and regulatory reporting. Supports use cases
 * such as payment status tracking, channel-specific volume monitoring,
 * large-value threshold alerting, and comprehensive date-range search
 * with optional filter criteria.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code paymentRef} field of type
 * {@link String}, which serves as the unique payment reference
 * identifier assigned at creation time.
 * </p>
 */
@Repository
public interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, String> {

    /**
     * Find all payment instructions with the given status.
     *
     * @param status the payment status code (e.g. PEND, PROC, COMP, CANC, RJCT)
     * @return list of payment instructions in the specified status, never {@code null}
     */
    List<PaymentInstruction> findByStatus(String status);

    /**
     * Find payment instructions by channel and status combination.
     * Useful for monitoring throughput per channel and filtering
     * payments awaiting processing on a specific channel.
     *
     * @param channel the payment channel identifier (e.g. SWIFT, FASTER, GIRO)
     * @param status  the payment status code
     * @return list of matching payment instructions, never {@code null}
     */
    List<PaymentInstruction> findByChannelAndStatus(String channel, String status);

    /**
     * Find active payments for a given value date by excluding a
     * specific terminal status. Typically used to retrieve all
     * non-cancelled and non-rejected payments settling on a
     * particular date.
     *
     * @param date   the value date for which payments settle
     * @param status the terminal status to exclude (e.g. CANC or RJCT)
     * @return list of active payments for the date, never {@code null}
     */
    List<PaymentInstruction> findByValueDateAndStatusNot(LocalDate date, String status);

    /**
     * Find all payments originating from a specific payer account on
     * a given value date. Supports account-level position monitoring
     * and intraday liquidity tracking.
     *
     * @param account the payer account number
     * @param date    the value date
     * @return list of payments for the account and date, never {@code null}
     */
    List<PaymentInstruction> findByPayerAccountAndValueDate(String account, LocalDate date);

    /**
     * Retrieve a payment instruction by its bank-generated reference
     * number. Used when external systems or counterparties reference
     * a payment by the bank's own identifier rather than the payment
     * reference.
     *
     * @param bankRef the bank reference number assigned during processing
     * @return an {@link Optional} containing the payment if found,
     *         or {@link Optional#empty()} otherwise
     */
    Optional<PaymentInstruction> findByBankRef(String bankRef);

    /**
     * Count active payments for a specific channel on a given value
     * date, excluding a terminal status. Supports volume-based
     * monitoring and capacity planning for payment channels.
     *
     * @param channel the payment channel identifier
     * @param date    the value date
     * @param status  the terminal status to exclude (e.g. CANC)
     * @return the count of active payments matching the criteria
     */
    long countByChannelAndValueDateAndStatusNot(String channel, LocalDate date, String status);

    /**
     * Sum the total amount of active payments for a channel on a value
     * date, excluding a terminal status. Returns zero if no payments
     * match. Used for channel-level exposure and liquidity monitoring.
     *
     * @param channel the payment channel identifier
     * @param date    the value date
     * @param status  the terminal status to exclude (e.g. CANC or RJCT)
     * @return the total amount as a {@link BigDecimal}, zero if no
     *         matching payments exist
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentInstruction p "
         + "WHERE p.channel = :channel AND p.valueDate = :date AND p.status <> :status")
    BigDecimal sumAmountByChannelAndValueDateAndStatusNot(@Param("channel") String channel,
                                                           @Param("date") LocalDate date,
                                                           @Param("status") String status);

    /**
     * Find payer accounts whose accumulated daily payment amounts
     * exceed a specified threshold. This is used for large-value
     * transaction monitoring and regulatory reporting (e.g. MAS
     * large-value reporting requirements).
     *
     * <p>
     * The query groups payments by payer account, sums their amounts
     * for the given value date (excluding cancelled and rejected
     * payments), and returns only those groups whose total exceeds
     * the threshold.
     * </p>
     *
     * @param date      the value date to analyse
     * @param threshold the minimum accumulated amount for inclusion
     * @return list of {@code Object[]} where each element contains
     *         {@code [0] = payerAccount (String)} and
     *         {@code [1] = totalAmount (BigDecimal)}
     */
    @Query("SELECT p.payerAccount, SUM(p.amount) FROM PaymentInstruction p "
         + "WHERE p.valueDate = :date AND p.status NOT IN ('CANC', 'RJCT') "
         + "GROUP BY p.payerAccount HAVING SUM(p.amount) > :threshold")
    List<Object[]> findDailyAccumulatedAmountByAccount(@Param("date") LocalDate date,
                                                        @Param("threshold") BigDecimal threshold);

    /**
     * Search payment instructions within a creation timestamp range
     * with optional filters for channel, status, and currency.
     * <p>
     * All optional parameters accept {@code null} to indicate
     * "no filter applied" for that dimension. This method supports
     * flexible ad-hoc queries used in operational dashboards and
     * audit trail lookups.
     * </p>
     *
     * @param start    the inclusive start of the creation time range
     * @param end      the inclusive end of the creation time range
     * @param channel  optional channel filter, or {@code null} to ignore
     * @param status   optional status filter, or {@code null} to ignore
     * @param currency optional currency filter, or {@code null} to ignore
     * @return list of payment instructions matching all non-null criteria,
     *         never {@code null}
     */
    @Query("SELECT p FROM PaymentInstruction p WHERE p.createTime BETWEEN :start AND :end "
         + "AND (:channel IS NULL OR p.channel = :channel) "
         + "AND (:status IS NULL OR p.status = :status) "
         + "AND (:currency IS NULL OR p.currency = :currency)")
    List<PaymentInstruction> findPaymentsByDateRange(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("channel") String channel,
                                                      @Param("status") String status,
                                                      @Param("currency") String currency);
}
