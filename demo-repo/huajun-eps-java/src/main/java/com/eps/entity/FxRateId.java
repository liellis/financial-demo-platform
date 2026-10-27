package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite primary key for {@link FxRate} entity.
 * A currency pair on a given effective date uniquely identifies a rate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateId implements Serializable {

    /** Source currency code (ISO 4217), e.g. USD */
    private String fromCurrency;

    /** Target currency code (ISO 4217), e.g. SGD */
    private String toCurrency;

    /** Effective date of this exchange rate */
    private LocalDate rateDate;
}
