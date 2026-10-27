package com.eps.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe utility class for generating unique payment reference numbers
 * and related identifiers throughout the Electronic Payment System (EPS).
 *
 * <p>All generated references follow a structured format similar to AS/400 EPSREF
 * generation: a 3-character prefix identifying the reference type, followed by
 * an 8-digit date in YYYYMMDD format, followed by an 8-digit sequence number
 * that auto-increments and resets daily.</p>
 *
 * <p>This class can be used via Spring injection ({@code @Autowired}) or
 * through its static convenience methods. When used as a Spring component,
 * the singleton guarantees thread-safe sequence generation. Static methods
 * delegate to an internal singleton instance.</p>
 *
 * <p><strong>Reference Formats:</strong></p>
 * <ul>
 *   <li>Payment Reference: {@code EPS2026072900000001}</li>
 *   <li>Batch Number:      {@code BTH2026072900000001}</li>
 *   <li>Match ID:          {@code MTC2026072900000001}</li>
 *   <li>GL Voucher Number: {@code GLV2026072900000001}</li>
 * </ul>
 */
@Component
public class PaymentRefGenerator {

    /** Formatter for the date portion of reference numbers (YYYYMMDD). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Number of digits for the sequence portion of each reference. */
    private static final int SEQUENCE_LENGTH = 8;

    /** Format string for zero-padded sequence of 8 digits. */
    private static final String SEQUENCE_FORMAT = "%08d";

    /** Maximum value before rolling over: 99,999,999. */
    private static final long MAX_SEQUENCE = 99_999_999L;

    /** Internal singleton for static method delegation. */
    private static final PaymentRefGenerator INSTANCE = new PaymentRefGenerator();

    /** Thread-safe sequence counter using atomic operations. */
    private final AtomicLong sequence;

    /** Stores the last date seen, for daily reset detection. */
    private volatile String lastDate;

    /** Lock for thread-safe date transition. */
    private final ReentrantLock resetLock;

    /**
     * Default constructor. Initializes the sequence counter and
     * locks for the current date. When used as a Spring bean,
     * this constructor is called once per application context.
     */
    public PaymentRefGenerator() {
        this.sequence = new AtomicLong(0);
        this.lastDate = LocalDate.now().format(DATE_FORMATTER);
        this.resetLock = new ReentrantLock();
    }

    // ========================================================================
    // Static convenience methods — delegate to singleton instance
    // ========================================================================

    /**
     * Generates a unique payment reference number.
     * Format: {@code EPS + YYYYMMDD + 8-digit sequence}.
     *
     * @return a unique payment reference string, e.g. {@code EPS2026072900000001}
     */
    public static String generatePaymentRef() {
        return INSTANCE.generate("EPS");
    }

    /**
     * Generates a unique batch processing number.
     * Format: {@code BTH + YYYYMMDD + 8-digit sequence}.
     *
     * @return a unique batch number string, e.g. {@code BTH2026072900000001}
     */
    public static String generateBatchNo() {
        return INSTANCE.generate("BTH");
    }

    /**
     * Generates a unique reconciliation match identifier.
     * Format: {@code MTC + YYYYMMDD + 8-digit sequence}.
     *
     * @return a unique match ID string, e.g. {@code MTC2026072900000001}
     */
    public static String generateMatchId() {
        return INSTANCE.generate("MTC");
    }

    /**
     * Generates a unique general ledger voucher number.
     * Format: {@code GLV + YYYYMMDD + 8-digit sequence}.
     *
     * @return a unique voucher number string, e.g. {@code GLV2026072900000001}
     */
    public static String generateVoucherNo() {
        return INSTANCE.generate("GLV");
    }

    // ========================================================================
    // Instance method for injection-based usage
    // ========================================================================

    /**
     * Instance-level generator method. Invoke on an injected bean
     * to generate a reference with a custom prefix.
     *
     * <p>This method is thread-safe. The sequence counter is an
     * {@link AtomicLong}, and date reset is guarded by a
     * {@link ReentrantLock} to prevent race conditions during
     * midnight rollover.</p>
     *
     * @param prefix the 3-character prefix identifying the reference type
     * @return the generated reference string
     */
    public String generate(String prefix) {
        checkAndResetForNewDay();
        long seq = sequence.incrementAndGet();
        return prefix + lastDate + String.format(SEQUENCE_FORMAT, seq);
    }

    // ========================================================================
    // Private helper methods
    // ========================================================================

    /**
     * Checks whether the date has changed since the last invocation
     * and resets the sequence counter to zero if it has. This implements
     * the daily sequence reset behavior of AS/400 EPSREF.
     *
     * <p>Uses a double-checked locking pattern with a {@code volatile}
     * date field for efficient read-mostly access.</p>
     */
    private void checkAndResetForNewDay() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        if (!today.equals(lastDate)) {
            resetLock.lock();
            try {
                // Double-check inside lock to prevent multiple resets
                if (!today.equals(lastDate)) {
                    sequence.set(0);
                    lastDate = today;
                }
            } finally {
                resetLock.unlock();
            }
        }

        // Safety check: if sequence somehow exceeds max, reset it
        if (sequence.get() > MAX_SEQUENCE) {
            resetLock.lock();
            try {
                if (sequence.get() > MAX_SEQUENCE) {
                    sequence.set(0);
                }
            } finally {
                resetLock.unlock();
            }
        }
    }
}
