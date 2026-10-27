package com.eps.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for currency precision rounding and monetary arithmetic
 * operations, modeled after AS/400 EPSNUM {@code RoundAmount}.
 *
 * <p>Different currencies require different numbers of decimal places
 * for proper rounding. This utility encapsulates those rules and provides
 * null-safe arithmetic methods for {@link BigDecimal} values commonly
 * used throughout the EPS payment processing pipeline.</p>
 *
 * <p><strong>Rounding Rules by Currency:</strong></p>
 * <ul>
 *   <li>0 decimals: JPY, KRW, IDR, VND</li>
 *   <li>3 decimals: BHD, KWD, OMR</li>
 *   <li>2 decimals: all other currencies (default)</li>
 * </ul>
 */
public final class CurrencyUtil {

    /** Currencies that round to 0 decimal places. */
    private static final Set<String> ZERO_DECIMAL_CURRENCIES =
            Set.of("JPY", "KRW", "IDR", "VND");

    /** Currencies that round to 3 decimal places. */
    private static final Set<String> THREE_DECIMAL_CURRENCIES =
            Set.of("BHD", "KWD", "OMR");

    /** Default number of decimal places for most currencies. */
    private static final int DEFAULT_DECIMALS = 2;

    /** Private constructor to prevent instantiation. */
    private CurrencyUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // Rounding
    // ========================================================================

    /**
     * Rounds a monetary amount to the appropriate decimal precision
     * for the specified currency, using {@link RoundingMode#HALF_UP}.
     *
     * <p>Rounding rules are sourced from ISO 4217 conventions used
     * by SWIFT and major payment systems:</p>
     * <ul>
     *   <li>JPY, KRW, IDR, VND have 0 minor units (integer amounts)</li>
     *   <li>BHD, KWD, OMR have 3 minor units</li>
     *   <li>All other currencies default to 2 minor units</li>
     * </ul>
     *
     * @param amount   the amount to round; if null, {@link BigDecimal#ZERO} is returned
     * @param currency the ISO 4217 currency code (e.g. "SGD", "USD"); case-insensitive
     * @return the rounded amount, never null
     */
    public static BigDecimal round(BigDecimal amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        int decimals = getDecimalsForCurrency(currency);
        return amount.setScale(decimals, RoundingMode.HALF_UP);
    }

    /**
     * Rounds a monetary amount with a custom {@link RoundingMode}.
     *
     * @param amount       the amount to round; if null, {@link BigDecimal#ZERO} is returned
     * @param currency     the ISO 4217 currency code; case-insensitive
     * @param roundingMode the rounding mode to apply
     * @return the rounded amount, never null
     */
    public static BigDecimal round(BigDecimal amount, String currency, RoundingMode roundingMode) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        int decimals = getDecimalsForCurrency(currency);
        return amount.setScale(decimals, roundingMode);
    }

    /**
     * Determines the number of decimal places (minor units) for
     * a given ISO 4217 currency code.
     *
     * @param currency the ISO 4217 currency code; case-insensitive
     * @return the number of decimal places (0, 2, or 3)
     */
    public static int getDecimalsForCurrency(String currency) {
        if (currency == null) {
            return DEFAULT_DECIMALS;
        }
        String upper = currency.toUpperCase().trim();
        if (ZERO_DECIMAL_CURRENCIES.contains(upper)) {
            return 0;
        }
        if (THREE_DECIMAL_CURRENCIES.contains(upper)) {
            return 3;
        }
        return DEFAULT_DECIMALS;
    }

    // ========================================================================
    // Formatting
    // ========================================================================

    /**
     * Formats a monetary amount with the appropriate currency symbol
     * and decimal precision for human-readable display.
     *
     * <p>Uses {@link NumberFormat#getCurrencyInstance()} with the
     * default locale. For production use with locale-aware formatting,
     * consider the overload that accepts a {@link Locale}.</p>
     *
     * @param amount   the amount to format; if null, "0.00" is returned
     * @param currency the ISO 4217 currency code
     * @return a formatted currency string, e.g. "SGD1,234.56" or "$1,234.56"
     */
    public static String formatCurrency(BigDecimal amount, String currency) {
        return formatCurrency(amount, currency, Locale.getDefault());
    }

    /**
     * Formats a monetary amount with the appropriate currency symbol,
     * decimal precision, and locale.
     *
     * @param amount   the amount to format; if null, "0.00" is returned
     * @param currency the ISO 4217 currency code
     * @param locale   the locale for formatting conventions
     * @return a formatted currency string
     */
    public static String formatCurrency(BigDecimal amount, String currency, Locale locale) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        String isoCode = currency != null ? currency.toUpperCase().trim() : "SGD";
        int decimals = getDecimalsForCurrency(isoCode);

        NumberFormat fmt = NumberFormat.getCurrencyInstance(locale);
        fmt.setMinimumFractionDigits(decimals);
        fmt.setMaximumFractionDigits(decimals);

        try {
            Currency javaCurrency = Currency.getInstance(isoCode);
            fmt.setCurrency(javaCurrency);
        } catch (IllegalArgumentException e) {
            // If Java doesn't recognize the currency, format as plain number
            return safeAmount.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
        }

        return fmt.format(safeAmount.setScale(decimals, RoundingMode.HALF_UP));
    }

    // ========================================================================
    // Conversion
    // ========================================================================

    /**
     * Applies a foreign exchange rate to convert an amount from one currency
     * to another, with appropriate rounding for the target currency.
     *
     * <p>The formula is: {@code amount × rate}, rounded to the target
     * currency's decimal precision using {@link RoundingMode#HALF_UP}.</p>
     *
     * @param amount         the source amount; if null, {@link BigDecimal#ZERO} is returned
     * @param rate           the exchange rate (target currency per unit of source); if null, returns the original amount
     * @param targetCurrency the ISO 4217 code of the target currency
     * @return the converted amount rounded to the target currency's precision, never null
     */
    public static BigDecimal convert(BigDecimal amount, BigDecimal rate, String targetCurrency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (rate == null) {
            return round(amount, targetCurrency);
        }
        BigDecimal converted = amount.multiply(rate);
        return round(converted, targetCurrency);
    }

    // ========================================================================
    // Null-safe arithmetic
    // ========================================================================

    /**
     * Performs null-safe addition of multiple {@link BigDecimal} values.
     * Any null argument is treated as {@link BigDecimal#ZERO}.
     *
     * @param amounts the amounts to sum; null array returns {@link BigDecimal#ZERO}
     * @return the sum of all non-null amounts, never null
     */
    public static BigDecimal add(BigDecimal... amounts) {
        if (amounts == null || amounts.length == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount != null) {
                result = result.add(amount);
            }
        }
        return result;
    }

    /**
     * Performs null-safe subtraction: {@code a - b}.
     * Null arguments are treated as {@link BigDecimal#ZERO}.
     *
     * @param a the minuend; null treated as zero
     * @param b the subtrahend; null treated as zero
     * @return the result of a minus b, never null
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal safeA = a != null ? a : BigDecimal.ZERO;
        BigDecimal safeB = b != null ? b : BigDecimal.ZERO;
        return safeA.subtract(safeB);
    }

    /**
     * Performs null-safe multiplication: {@code a × b}.
     * Null arguments are treated as {@link BigDecimal#ZERO}.
     *
     * @param a the multiplicand; null treated as zero
     * @param b the multiplier; null treated as zero
     * @return the product, never null
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        BigDecimal safeA = a != null ? a : BigDecimal.ZERO;
        BigDecimal safeB = b != null ? b : BigDecimal.ZERO;
        return safeA.multiply(safeB);
    }

    /**
     * Checks whether an amount is positive (greater than zero).
     * Null is treated as zero (returns false).
     *
     * @param amount the amount to check
     * @return true if the amount is greater than zero
     */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks whether an amount is zero or null.
     *
     * @param amount the amount to check
     * @return true if the amount is null or equal to zero
     */
    public static boolean isNullOrZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
