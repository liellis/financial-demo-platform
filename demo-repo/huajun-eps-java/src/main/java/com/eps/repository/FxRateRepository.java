package com.eps.repository;

import com.eps.entity.FxRate;
import com.eps.entity.FxRateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FxRate} entity.
 * <p>
 * Provides query methods for foreign exchange rate management.
 * FX rates are used for currency conversion during cross-currency
 * payment processing, enabling accurate amount calculations when
 * the payment currency differs from the settlement or account
 * currencies.
 * </p>
 *
 * <p>
 * The entity uses a composite primary key defined by
 * {@link FxRateId}, consisting of {@code fromCurrency},
 * {@code toCurrency}, and {@code rateDate}. Each combination
 * of these three attributes is unique.
 * </p>
 */
@Repository
public interface FxRateRepository extends JpaRepository<FxRate, FxRateId> {

    /**
     * Retrieve the FX rate for a specific currency pair on a
     * particular date. Used during payment processing to look up
     * the applicable exchange rate for converting between two
     * currencies at the value date.
     *
     * @param from the source ISO 4217 currency code (e.g. USD)
     * @param to   the target ISO 4217 currency code (e.g. SGD)
     * @param date the rate date for which the rate is effective
     * @return an {@link Optional} containing the matching
     *         {@link FxRate} if one exists for the given currency
     *         pair and date, or {@link Optional#empty()} otherwise
     */
    Optional<FxRate> findByFromCurrencyAndToCurrencyAndRateDate(String from,
                                                                  String to,
                                                                  LocalDate date);
}
