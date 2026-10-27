package com.eps.repository;

import com.eps.entity.ReconMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JPA repository for {@link ReconMatch} entity.
 * <p>
 * Provides query methods for reconciliation match management.
 * Each reconciliation match links a payment instruction to one or
 * more statement lines, recording the match status, date, and the
 * user or system that performed the matching operation.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code matchId} field of type
 * {@link String}, assigned when the match record is created.
 * </p>
 */
@Repository
public interface ReconMatchRepository extends JpaRepository<ReconMatch, String> {

    /**
     * Find all reconciliation matches with the given match status.
     * Supports reconciliation monitoring dashboards and exception
     * handling workflows.
     *
     * @param status the match status code (e.g. AUTO, MANUAL, DISPUTED)
     * @return list of reconciliation matches in the specified status,
     *         never {@code null}
     */
    List<ReconMatch> findByMatchStatus(String status);

    /**
     * Generate an exception report grouped by match status and date
     * within a specified date range. This query aggregates match
     * counts to highlight reconciliation anomalies and trends over
     * time.
     *
     * <p>
     * The result is ordered by match date and then match status for
     * consistent chronological reporting.
     * </p>
     *
     * @param start the inclusive start of the date range
     * @param end   the inclusive end of the date range
     * @return list of {@code Object[]} where each element contains
     *         {@code [0] = matchStatus (String)},
     *         {@code [1] = matchDate (LocalDate)}, and
     *         {@code [2] = count (Long)}
     */
    @Query("SELECT r.matchStatus, r.matchDate, COUNT(r) FROM ReconMatch r "
         + "WHERE r.matchDate BETWEEN :start AND :end "
         + "GROUP BY r.matchStatus, r.matchDate "
         + "ORDER BY r.matchDate, r.matchStatus")
    List<Object[]> findExceptionReportGroupByStatusAndDate(@Param("start") LocalDate start,
                                                            @Param("end") LocalDate end);
}
