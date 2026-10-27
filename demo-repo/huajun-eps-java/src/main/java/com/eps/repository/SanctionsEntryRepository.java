package com.eps.repository;

import com.eps.entity.SanctionsEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SanctionsEntry} entity.
 * <p>
 * Provides query methods for sanctions screening. Sanctions entries
 * represent individuals, entities, or vessels that appear on
 * regulatory sanctions lists. During payment processing, payer
 * and beneficiary names are screened against these entries to
 * ensure compliance with international sanctions regimes.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code entryId} field of type
 * {@link String}, assigned when the sanctions list entry is loaded.
 * </p>
 */
@Repository
public interface SanctionsEntryRepository extends JpaRepository<SanctionsEntry, String> {

    /**
     * Find sanctions entries whose entity name contains the given
     * substring. This is a Spring Data derived query method using
     * the {@code Containing} keyword, which generates a SQL
     * {@code LIKE '%name%'} clause. The matching is case-sensitive
     * by default.
     *
     * @param name the partial or full entity name to search for
     * @return list of sanctions entries whose name contains the
     *         specified substring, never {@code null}
     */
    List<SanctionsEntry> findByEntityNameContaining(String name);

    /**
     * Perform a case-insensitive fuzzy match on sanctions entity
     * names. This custom JPQL query converts both the stored entity
     * name and the search parameter to lowercase before comparison,
     * using a {@code LIKE} pattern with wildcards on both sides.
     *
     * <p>
     * This method is used during real-time sanctions screening to
     * identify potential name matches regardless of case
     * differences in the input data.
     * </p>
     *
     * @param name the entity name fragment to search for
     *             (case-insensitive)
     * @return list of sanctions entries matching the name pattern,
     *         never {@code null}
     */
    @Query("SELECT s FROM SanctionsEntry s "
         + "WHERE LOWER(s.entityName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<SanctionsEntry> fuzzyMatchByName(@Param("name") String name);
}
