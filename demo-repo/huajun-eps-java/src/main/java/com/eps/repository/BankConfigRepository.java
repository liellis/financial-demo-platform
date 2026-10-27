package com.eps.repository;

import com.eps.entity.BankConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link BankConfig} entity.
 * <p>
 * Provides query methods for bank configuration management.
 * Bank configuration records store operational parameters,
 * connectivity settings, and processing rules for each
 * correspondent or partner bank integrated with the EPS.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code bankCode} field of type
 * {@link String}, which serves as the unique identifier for each
 * configured bank.
 * </p>
 */
@Repository
public interface BankConfigRepository extends JpaRepository<BankConfig, String> {

    /**
     * Find all bank configurations with the given operational
     * status. Used to retrieve only active banks for payment
     * routing decisions, or to list suspended/disabled banks
     * for administrative review.
     *
     * @param status the bank configuration status code
     *               (e.g. ACTIVE, SUSPENDED, DISABLED)
     * @return list of bank configurations matching the specified
     *         status, never {@code null}
     */
    List<BankConfig> findByStatus(String status);
}
