package com.eps.repository;

import com.eps.entity.StatementLine;
import com.eps.entity.StatementLineId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link StatementLine} entity.
 * <p>
 * Provides query methods for bank statement line management and
 * reconciliation workflows. Statement lines represent individual
 * entries within a bank statement, identified by a composite key
 * composed of {@code statementNo}, {@code entryRef}, and
 * {@code lineNo}.
 * </p>
 *
 * <p>
 * The composite primary key is implemented via
 * {@link StatementLineId} and declared on the entity with
 * {@code @IdClass(StatementLineId.class)}.
 * </p>
 */
@Repository
public interface StatementLineRepository extends JpaRepository<StatementLine, StatementLineId> {

    /**
     * Find all statement lines with the given reconciliation status.
     * Supports reconciliation dashboards that group statement entries
     * by their matching state.
     *
     * @param status the reconciliation status code (e.g. MATCHED,
     *               UNMATCHED, PARTIAL)
     * @return list of statement lines with the specified recon status,
     *         never {@code null}
     */
    List<StatementLine> findByReconStatus(String status);

    /**
     * Find all statement lines for a specific bank code on a given
     * value date. Used when processing daily bank statements and
     * preparing entries for the reconciliation engine.
     *
     * @param bankCode the bank identifier code
     * @param date     the value date of the statement entries
     * @return list of statement lines for the bank and date,
     *         never {@code null}
     */
    List<StatementLine> findByBankCodeAndValueDate(String bankCode, LocalDate date);

    /**
     * Find a statement line by its bank-generated reference number.
     * Used during auto-reconciliation to match statement entries
     * against payment instructions.
     *
     * @param bankRef the bank reference number from the statement
     * @return an {@link Optional} containing the statement line if
     *         found, or {@link Optional#empty()} otherwise
     */
    Optional<StatementLine> findByBankRef(String bankRef);
}
