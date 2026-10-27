package com.eps.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates business days in the Singapore banking calendar, skipping
 * weekends (Saturday and Sunday) and Singapore public holidays.
 *
 * <p>This component models the AS/400 EPS date calculation routines used
 * for value date determination, settlement scheduling, and cutoff time
 * enforcement. Singapore public holidays for 2025–2027 are pre-loaded.</p>
 *
 * <p>This class is annotated with {@code @Component} for Spring injection.
 * Use {@code @Autowired} to obtain an instance, or instantiate directly
 * for standalone use.</p>
 */
@Component
public class BusinessDayCalculator {

    /** Weekend days observed in Singapore. */
    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /** Cache for holiday lookups to avoid repeated set scans. */
    private final Map<LocalDate, String> holidayCache;

    /** Thread-safe set of known Singapore public holidays. */
    private final Set<LocalDate> holidays;

    /**
     * Constructs a new calculator pre-loaded with Singapore public holidays
     * for the years 2025 through 2027.
     *
     * <p>Holidays are loaded into an immutable set for fast lookup.
     * The holiday cache supports concurrent access for performance
     * in multi-threaded payment processing.</p>
     */
    public BusinessDayCalculator() {
        this.holidayCache = new ConcurrentHashMap<>();
        this.holidays = buildSingaporeHolidays();
    }

    // ========================================================================
    // Business day queries
    // ========================================================================

    /**
     * Checks whether a given date is a business day in Singapore.
     * A business day is any Monday through Friday that is not
     * a Singapore public holiday.
     *
     * @param date the date to check; if null, returns false
     * @return true if the date is a Singapore business day
     */
    public boolean isBusinessDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        return !WEEKEND.contains(date.getDayOfWeek()) && !isSingaporeHoliday(date);
    }

    /**
     * Finds the next business day after (and including) the given date.
     * If the given date is itself a business day, it is returned as-is.
     *
     * @param date the starting date; if null, returns today
     * @return the next business day, never null
     */
    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate current = date != null ? date : LocalDate.now();
        while (!isBusinessDay(current)) {
            current = current.plusDays(1);
        }
        return current;
    }

    /**
     * Finds the next business day strictly after the given date
     * (the given date is excluded even if it is a business day).
     *
     * @param date the reference date; if null, starts from today
     * @return the business day following the given date, never null
     */
    public LocalDate followingBusinessDay(LocalDate date) {
        LocalDate current = (date != null ? date : LocalDate.now()).plusDays(1);
        while (!isBusinessDay(current)) {
            current = current.plusDays(1);
        }
        return current;
    }

    /**
     * Finds the previous business day before (and including) the given date.
     * If the given date is itself a business day, it is returned as-is.
     *
     * @param date the starting date; if null, returns today
     * @return the previous business day, never null
     */
    public LocalDate previousBusinessDay(LocalDate date) {
        LocalDate current = date != null ? date : LocalDate.now();
        while (!isBusinessDay(current)) {
            current = current.minusDays(1);
        }
        return current;
    }

    /**
     * Adds a number of business days to a date, skipping weekends
     * and Singapore public holidays. The starting date is always
     * advanced to the next business day before counting begins.
     *
     * @param date the starting date; if null, starts from today
     * @param days the number of business days to add; if negative, subtracts
     * @return the resulting business day, never null
     */
    public LocalDate addBusinessDays(LocalDate date, int days) {
        LocalDate current = nextBusinessDay(date);
        int remaining = Math.abs(days);
        int step = days >= 0 ? 1 : -1;

        while (remaining > 0) {
            current = current.plusDays(step);
            if (isBusinessDay(current)) {
                remaining--;
            }
        }
        return current;
    }

    /**
     * Counts the number of business days between two dates (inclusive of start,
     * exclusive of end). Returns zero if either date is null or if start is
     * after end.
     *
     * @param start the start date (inclusive)
     * @param end   the end date (exclusive)
     * @return the number of business days in the range
     */
    public long getBusinessDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) {
            return 0L;
        }
        long count = 0;
        LocalDate current = start;
        while (current.isBefore(end)) {
            if (isBusinessDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    // ========================================================================
    // Holiday detection
    // ========================================================================

    /**
     * Determines whether a given date is a Singapore public holiday.
     *
     * <p>Results are cached after first lookup for performance.</p>
     *
     * @param date the date to check; if null, returns false
     * @return true if the date is a known Singapore public holiday
     */
    public boolean isSingaporeHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return holidayCache.computeIfAbsent(date, d ->
                holidays.contains(d) ? "HOLIDAY" : null
        ) != null;
    }

    /**
     * Returns the name of the holiday for a given date, or null if
     * the date is not a holiday.
     *
     * @param date the date to look up; may be null
     * @return the holiday name, or null
     */
    public String getHolidayName(LocalDate date) {
        if (date == null) {
            return null;
        }
        String cached = holidayCache.get(date);
        if (cached != null && !"HOLIDAY".equals(cached)) {
            return cached;
        }
        if (holidays.contains(date)) {
            // Rebuild to find the name
            return findHolidayName(date);
        }
        return null;
    }

    // ========================================================================
    // Holiday data
    // ========================================================================

    /**
     * Builds the set of known Singapore public holidays for 2025-2027.
     *
     * <p><strong>Note:</strong> Some holidays (e.g. Hari Raya Puasa,
     * Hari Raya Haji, Deepavali) are based on the Islamic or Hindu lunar
     * calendar. The dates used here are estimated/projected for the
     * relevant years and should be updated annually with official
     * MOM gazette notifications.</p>
     *
     * @return an unmodifiable set of holiday dates
     */
    private Set<LocalDate> buildSingaporeHolidays() {
        return Set.of(
                // === 2025 ===
                LocalDate.of(2025, 1, 1),    // New Year's Day
                LocalDate.of(2025, 1, 29),   // Chinese New Year (Day 1)
                LocalDate.of(2025, 1, 30),   // Chinese New Year (Day 2)
                LocalDate.of(2025, 4, 18),   // Good Friday
                LocalDate.of(2025, 3, 31),   // Hari Raya Puasa (estimated)
                LocalDate.of(2025, 5, 1),    // Labour Day
                LocalDate.of(2025, 5, 12),   // Vesak Day
                LocalDate.of(2025, 6, 7),    // Hari Raya Haji (estimated)
                LocalDate.of(2025, 8, 9),    // National Day
                LocalDate.of(2025, 10, 20),  // Deepavali (estimated)
                LocalDate.of(2025, 12, 25),  // Christmas Day

                // === 2026 ===
                LocalDate.of(2026, 1, 1),    // New Year's Day
                LocalDate.of(2026, 2, 17),   // Chinese New Year (Day 1)
                LocalDate.of(2026, 2, 18),   // Chinese New Year (Day 2)
                LocalDate.of(2026, 4, 3),    // Good Friday
                LocalDate.of(2026, 3, 20),   // Hari Raya Puasa (estimated)
                LocalDate.of(2026, 5, 1),    // Labour Day
                LocalDate.of(2026, 5, 1),    // Vesak Day (may differ; update with actual)
                LocalDate.of(2026, 5, 27),   // Hari Raya Haji (estimated)
                LocalDate.of(2026, 8, 9),    // National Day (Sunday; Monday holiday)
                LocalDate.of(2026, 8, 10),   // National Day observed
                LocalDate.of(2026, 11, 8),   // Deepavali (estimated)
                LocalDate.of(2026, 12, 25),  // Christmas Day

                // === 2027 ===
                LocalDate.of(2027, 1, 1),    // New Year's Day
                LocalDate.of(2027, 2, 6),    // Chinese New Year (Day 1)
                LocalDate.of(2027, 2, 7),    // Chinese New Year (Day 2)
                LocalDate.of(2027, 3, 26),   // Good Friday
                LocalDate.of(2027, 3, 9),    // Hari Raya Puasa (estimated)
                LocalDate.of(2027, 5, 1),    // Labour Day
                LocalDate.of(2027, 5, 20),   // Vesak Day (estimated)
                LocalDate.of(2027, 5, 17),   // Hari Raya Haji (estimated)
                LocalDate.of(2027, 8, 9),    // National Day
                LocalDate.of(2027, 10, 29),  // Deepavali (estimated)
                LocalDate.of(2027, 12, 25)   // Christmas Day
        );
    }

    /**
     * Finds the name of a holiday for display purposes.
     * This is a simple lookup against known dates.
     *
     * @param date the holiday date
     * @return the holiday name, or null if not found
     */
    private String findHolidayName(LocalDate date) {
        int year = date.getYear();
        MonthDay md = MonthDay.from(date);

        // New Year's Day: January 1
        if (md.equals(MonthDay.of(1, 1))) return "New Year's Day";
        // Labour Day: May 1
        if (md.equals(MonthDay.of(5, 1))) return "Labour Day";
        // National Day: August 9
        if (md.equals(MonthDay.of(8, 9))) return "National Day";
        // Christmas Day: December 25
        if (md.equals(MonthDay.of(12, 25))) return "Christmas Day";

        // Year-dependent holidays
        if (year == 2025) {
            if (md.equals(MonthDay.of(1, 29))) return "Chinese New Year (Day 1)";
            if (md.equals(MonthDay.of(1, 30))) return "Chinese New Year (Day 2)";
            if (md.equals(MonthDay.of(4, 18))) return "Good Friday";
            if (md.equals(MonthDay.of(3, 31))) return "Hari Raya Puasa";
            if (md.equals(MonthDay.of(5, 12))) return "Vesak Day";
            if (md.equals(MonthDay.of(6, 7))) return "Hari Raya Haji";
            if (md.equals(MonthDay.of(10, 20))) return "Deepavali";
        } else if (year == 2026) {
            if (md.equals(MonthDay.of(2, 17))) return "Chinese New Year (Day 1)";
            if (md.equals(MonthDay.of(2, 18))) return "Chinese New Year (Day 2)";
            if (md.equals(MonthDay.of(4, 3))) return "Good Friday";
            if (md.equals(MonthDay.of(3, 20))) return "Hari Raya Puasa";
            if (md.equals(MonthDay.of(5, 1))) return "Vesak Day";
            if (md.equals(MonthDay.of(5, 27))) return "Hari Raya Haji";
            if (md.equals(MonthDay.of(8, 10))) return "National Day (observed)";
            if (md.equals(MonthDay.of(11, 8))) return "Deepavali";
        } else if (year == 2027) {
            if (md.equals(MonthDay.of(2, 6))) return "Chinese New Year (Day 1)";
            if (md.equals(MonthDay.of(2, 7))) return "Chinese New Year (Day 2)";
            if (md.equals(MonthDay.of(3, 26))) return "Good Friday";
            if (md.equals(MonthDay.of(3, 9))) return "Hari Raya Puasa";
            if (md.equals(MonthDay.of(5, 20))) return "Vesak Day";
            if (md.equals(MonthDay.of(5, 17))) return "Hari Raya Haji";
            if (md.equals(MonthDay.of(10, 29))) return "Deepavali";
        }

        return null;
    }
}
