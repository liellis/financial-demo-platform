package com.eps.util;

import com.eps.entity.PaymentInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds GIRO (General Interbank Recurring Order) files in both CSV and
 * fixed-length formats, modeled after AS/400 EPSGIRBLD.
 *
 * <p>GIRO is a widely used electronic payment mechanism in Singapore for
 * batch debit and credit transfers. This builder produces output files
 * suitable for upload to Singapore clearing systems (e.g. IBG, FAST GIRO).
 * Two output formats are supported:</p>
 * <ul>
 *   <li><strong>CSV format</strong> — comma-separated values with a header row,
 *       ideal for human review and spreadsheet processing</li>
 *   <li><strong>Fixed-length format</strong> — positional fields with precise
 *       column widths, required by many legacy banking systems</li>
 * </ul>
 *
 * <p>Both formats include optional header and trailer records for file-level
 * validation of record counts and total amounts.</p>
 */
public final class GiroFileBuilder {

    private static final Logger log = LoggerFactory.getLogger(GiroFileBuilder.class);

    /** Date format for GIRO file headers (YYYYMMDD). */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Line separator (platform-appropriate). */
    private static final String LINE_SEP = "\r\n";

    /** Private constructor to prevent instantiation. */
    private GiroFileBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // CSV format
    // ========================================================================

    /**
     * Builds a GIRO payment file in CSV format.
     *
     * <p>The output includes a header line with column names followed by
     * one data row per payment instruction. All values are properly quoted
     * if they contain commas, quotes, or newlines.</p>
     *
     * <p><strong>CSV Columns:</strong>
     * RecordType, BankCode, BranchCode, AccountNo, Amount, Currency,
     * RefNo, Narrative</p>
     *
     * @param payments the list of payment instructions (must not be null)
     * @return the complete CSV content as a string
     */
    public static String buildGiroCsv(List<PaymentInstruction> payments) {
        if (payments == null) {
            throw new IllegalArgumentException("Payments list must not be null");
        }

        log.debug("Building GIRO CSV for {} payments", payments.size());

        StringBuilder csv = new StringBuilder();

        // CSV header
        csv.append("RecordType,BankCode,BranchCode,AccountNo,Amount,Currency,RefNo,Narrative")
                .append(LINE_SEP);

        // Data rows
        for (PaymentInstruction payment : payments) {
            csv.append(buildGiroCsvRow(payment)).append(LINE_SEP);
        }

        return csv.toString();
    }

    /**
     * Builds a single CSV row from a payment instruction.
     */
    private static String buildGiroCsvRow(PaymentInstruction payment) {
        return csvField("D")                                    // RecordType (D = Detail)
                + "," + csvField(payment.getPayeeBank())        // BankCode
                + "," + csvField(payment.getPayeeBranch())      // BranchCode
                + "," + csvField(payment.getPayeeAccount())     // AccountNo
                + "," + csvField(formatAmount(payment.getAmount())) // Amount
                + "," + csvField(payment.getCurrency())         // Currency
                + "," + csvField(payment.getPaymentRef())       // RefNo
                + "," + csvField(payment.getRemittance());      // Narrative
    }

    /**
     * Formats a value for CSV output, handling nulls and special characters.
     *
     * @param value the raw value; null becomes empty string
     * @return the CSV-safe field value
     */
    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        // If the value contains comma, quote, or newline, wrap in quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ========================================================================
    // Fixed-length format
    // ========================================================================

    /**
     * Builds a GIRO payment file in fixed-length format.
     *
     * <p>Each field occupies a precise column position as specified below.
     * Fields shorter than their allocated width are padded with spaces
     * (strings left-justified, numbers right-justified).</p>
     *
     * <p><strong>Field Positions:</strong></p>
     * <pre>
     * RecordType  1    (1)   — D=detail, H=header, T=trailer
     * BankCode    10   (2-11)
     * BranchCode  10   (12-21)
     * AccountNo   30   (22-51)
     * Amount      15   (52-66)  — right-justified, 2 decimal places, no decimal point
     * Currency    3    (67-69)
     * RefNo       20   (70-89)
     * Narrative   140  (90-229)
     * Total: 229 characters
     * </pre>
     *
     * @param payments the list of payment instructions (must not be null)
     * @return the complete fixed-length content as a string
     */
    public static String buildGiroFixedLength(List<PaymentInstruction> payments) {
        if (payments == null) {
            throw new IllegalArgumentException("Payments list must not be null");
        }

        log.debug("Building GIRO fixed-length for {} payments", payments.size());

        StringBuilder sb = new StringBuilder();

        // Detail records
        for (PaymentInstruction payment : payments) {
            sb.append(buildFixedLengthRow(payment)).append(LINE_SEP);
        }

        return sb.toString();
    }

    /**
     * Builds a single fixed-length detail record.
     */
    private static String buildFixedLengthRow(PaymentInstruction payment) {
        StringBuilder row = new StringBuilder();

        // RecordType: 1 char, "D"
        row.append(fixedField("D", 1, true));

        // BankCode: 10 chars
        row.append(fixedField(payment.getPayeeBank(), 10, true));

        // BranchCode: 10 chars
        row.append(fixedField(payment.getPayeeBranch(), 10, true));

        // AccountNo: 30 chars
        row.append(fixedField(payment.getPayeeAccount(), 30, true));

        // Amount: 15 chars, right-justified, no decimal, padded with leading zeros
        row.append(fixedAmountField(payment.getAmount(), 15));

        // Currency: 3 chars
        row.append(fixedField(payment.getCurrency(), 3, true));

        // RefNo: 20 chars
        row.append(fixedField(payment.getPaymentRef(), 20, true));

        // Narrative: 140 chars
        row.append(fixedField(payment.getRemittance(), 140, true));

        return row.toString();
    }

    /**
     * Pads a string field to a fixed width.
     *
     * @param value      the raw value; null treated as empty
     * @param width      the target width
     * @param leftAlign  if true, left-justify with trailing spaces;
     *                   if false, right-justify with leading spaces
     * @return the padded field
     */
    private static String fixedField(String value, int width, boolean leftAlign) {
        String v = value != null ? value : "";
        if (v.length() > width) {
            v = v.substring(0, width);
        }
        if (leftAlign) {
            return String.format("%-" + width + "s", v);
        } else {
            return String.format("%" + width + "s", v);
        }
    }

    /**
     * Formats an amount as a fixed-length field.
     * Right-justified, zero-padded, no decimal separator.
     * E.g. 1234.56 with width 15 becomes "000000000123456".
     *
     * @param amount the amount; null treated as zero
     * @param width  the target width
     * @return the zero-padded amount string
     */
    private static String fixedAmountField(BigDecimal amount, int width) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        // Convert to string with 2 decimal places, remove the decimal point
        String amountStr = safeAmount.setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace(".", "");
        if (amountStr.length() > width) {
            // Truncate from the left (keep rightmost digits) if too wide
            amountStr = amountStr.substring(amountStr.length() - width);
        }
        // Left-pad with zeros
        return String.format("%0" + width + "d",
                amountStr.isEmpty() ? 0 : Long.parseLong(amountStr));
    }

    // ========================================================================
    // Header and Trailer records
    // ========================================================================

    /**
     * Builds a GIRO file header record (fixed-length format).
     *
     * <p>The header is a single record identifying the bank, value date,
     * and expected record count. Format: H followed by bank code, value date,
     * and record count.</p>
     *
     * @param bankCode    the originating bank code
     * @param valueDate   the value date for the batch
     * @param recordCount the number of detail records in the file
     * @return the header record string
     */
    public static String buildGiroHeader(String bankCode, LocalDate valueDate, int recordCount) {
        StringBuilder header = new StringBuilder();

        // RecordType: H
        header.append(fixedField("H", 1, true));

        // BankCode: 10 chars
        header.append(fixedField(bankCode, 10, true));

        // ValueDate: 8 chars (YYYYMMDD)
        String dateStr = valueDate != null
                ? valueDate.format(DATE_FORMAT)
                : LocalDate.now().format(DATE_FORMAT);
        header.append(fixedField(dateStr, 8, true));

        // RecordCount: 10 chars, right-justified
        header.append(fixedField(String.valueOf(recordCount), 10, false));

        // Filler: remaining to total width of 229
        int used = 1 + 10 + 8 + 10;
        int remaining = 229 - used;
        if (remaining > 0) {
            header.append(" ".repeat(remaining));
        }

        return header.toString();
    }

    /**
     * Builds a GIRO file trailer record (fixed-length format).
     *
     * <p>The trailer provides file-level validation with total record count
     * and total amount, allowing the receiving system to verify file integrity.</p>
     *
     * @param recordCount the total number of detail records
     * @param totalAmount the total amount of all payments
     * @return the trailer record string
     */
    public static String buildGiroTrailer(int recordCount, BigDecimal totalAmount) {
        StringBuilder trailer = new StringBuilder();

        // RecordType: T
        trailer.append(fixedField("T", 1, true));

        // RecordCount: 15 chars, right-justified
        trailer.append(fixedField(String.valueOf(recordCount), 15, false));

        // TotalAmount: 18 chars, right-justified
        trailer.append(fixedAmountField(totalAmount, 18));

        // Filler: remaining to total width
        int used = 1 + 15 + 18;
        int remaining = 229 - used;
        if (remaining > 0) {
            trailer.append(" ".repeat(remaining));
        }

        return trailer.toString();
    }

    /**
     * Builds a complete GIRO file in fixed-length format with header,
     * detail records, and trailer.
     *
     * @param payments the list of payment instructions
     * @param bankCode the originating bank code
     * @param valueDate the value date for the batch
     * @return the complete GIRO file content
     */
    public static String buildGiroFileComplete(List<PaymentInstruction> payments,
                                                String bankCode, LocalDate valueDate) {
        if (payments == null || payments.isEmpty()) {
            throw new IllegalArgumentException("Payments list must not be null or empty");
        }

        log.debug("Building complete GIRO file for {} payments, bank: {}", payments.size(), bankCode);

        // Calculate total amount
        BigDecimal totalAmount = payments.stream()
                .map(PaymentInstruction::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder file = new StringBuilder();

        // Header
        file.append(buildGiroHeader(bankCode, valueDate, payments.size())).append(LINE_SEP);

        // Detail records
        for (PaymentInstruction payment : payments) {
            file.append(buildFixedLengthRow(payment)).append(LINE_SEP);
        }

        // Trailer
        file.append(buildGiroTrailer(payments.size(), totalAmount)).append(LINE_SEP);

        return file.toString();
    }

    /**
     * Formats an amount for CSV output (plain decimal string).
     *
     * @param amount the amount; null returns "0.00"
     * @return the formatted amount string
     */
    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
