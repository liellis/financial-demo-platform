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
 * Foreign exchange rate entity, mapped to table EPSFXR.
 * Stores exchange rates for currency pairs sourced from market
 * data providers. Each rate is uniquely identified by the currency pair
 * and the effective date.
 *
 * Uses {@link FxRateId} as the composite primary key.
 */
@Entity
@Table(name = "EPSFXR")
@IdClass(FxRateId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRate {

    /** Source currency code (ISO 4217), e.g. USD — part of composite key */
    @Id
    @Column(name = "FROM_CURRENCY", length = 3, nullable = false)
    private String fromCurrency;

    /** Target currency code (ISO 4217), e.g. SGD — part of composite key */
    @Id
    @Column(name = "TO_CURRENCY", length = 3, nullable = false)
    private String toCurrency;

    /** Effective date of this exchange rate — part of composite key */
    @Id
    @Column(name = "RATE_DATE", nullable = false)
    private LocalDate rateDate;

    /** Exchange rate value (how many target units per 1 source unit) */
    @Column(name = "RATE", precision = 18, scale = 6)
    private BigDecimal rate;

    /**
     * Type of exchange rate.
     * SPOT = Current market rate, FORWARD = Forward contract rate.
     */
    @Column(name = "RATE_TYPE", length = 10)
    private String rateType;

    /** Start of the validity period for this rate */
    @Column(name = "VALID_FROM")
    private LocalDateTime validFrom;

    /** End of the validity period for this rate */
    @Column(name = "VALID_TO")
    private LocalDateTime validTo;

    /**
     * Source of the exchange rate data.
     * REUTERS, BLOOMBERG, MAS = Monetary Authority of Singapore.
     */
    @Column(name = "SOURCE", length = 10)
    private String source;

    /** Timestamp when this rate was loaded into the system */
    @Column(name = "LOAD_TIME")
    private LocalDateTime loadTime;
}
