package com.eps.repository;

import com.eps.entity.InternalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link InternalAccount} entity.
 * <p>
 * Provides query methods for internal (nostro/vostro) account
 * management. Internal accounts represent the bank's own accounts
 * held with correspondent banks, used for settlement, liquidity
 * management, and position keeping.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code accountNo} field of type
 * {@link String}, uniquely identifying each internal account.
 * </p>
 */
@Repository
public interface InternalAccountRepository extends JpaRepository<InternalAccount, String> {

    /**
     * Find all internal accounts associated with a specific
     * correspondent bank. Used to retrieve the complete set of
     * nostro accounts held with a given banking partner.
     *
     * @param bankCode the correspondent bank identifier code
     * @return list of internal accounts for the specified bank,
     *         never {@code null}
     */
    List<InternalAccount> findByBankCode(String bankCode);

    /**
     * Find all internal accounts with the given operational status.
     * Supports filtering active, suspended, or closed accounts for
     * settlement eligibility checks.
     *
     * @param status the account status code (e.g. ACTIVE, SUSPENDED, CLOSED)
     * @return list of internal accounts in the specified status,
     *         never {@code null}
     */
    List<InternalAccount> findByStatus(String status);

    /**
     * Find all internal accounts of a given currency with a specific
     * status. Useful for currency-level liquidity management, such
     * as retrieving all active USD nostro accounts for funding
     * decisions.
     *
     * @param currency the ISO 4217 currency code (e.g. USD, EUR, SGD)
     * @param status   the account status code
     * @return list of internal accounts matching both criteria,
     *         never {@code null}
     */
    List<InternalAccount> findByCurrencyAndStatus(String currency, String status);
}
