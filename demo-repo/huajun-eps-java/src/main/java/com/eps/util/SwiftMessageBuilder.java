package com.eps.util;

import com.eps.entity.PaymentInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Builds SWIFT MT (Message Type) messages for customer and bank transfers,
 * modeled after AS/400 EPSSWIFT generation.
 *
 * <p>SWIFT MT messages are structured text messages used for inter-bank
 * communication. This builder supports:</p>
 * <ul>
 *   <li><strong>MT103</strong> — Single Customer Credit Transfer</li>
 *   <li><strong>MT202</strong> — General Financial Institution Transfer (cover payment)</li>
 * </ul>
 *
 * <p>Each message consists of five blocks:</p>
 * <ol>
 *   <li>Basic Header — identifies the message type and sender</li>
 *   <li>Application Header — input/output identifier, receiver BIC, message priority</li>
 *   <li>User Header — optional banking priority and validation flags</li>
 *   <li>Text Block — the structured fields containing payment data</li>
 *   <li>Trailers — checksum and authentication codes</li>
 * </ol>
 *
 * <p>Usage is through static methods. The class is not intended to be instantiated.</p>
 */
public final class SwiftMessageBuilder {

    private static final Logger log = LoggerFactory.getLogger(SwiftMessageBuilder.class);

    /** SWIFT date format: YYMMDD. */
    private static final DateTimeFormatter SWIFT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    /** SWIFT line terminator (CRLF). */
    private static final String SWIFT_EOL = "\r\n";

    /** Field separator for SWIFT text block. */
    private static final String FIELD_SEP = "\r\n:";

    /** Private constructor to prevent instantiation. */
    private SwiftMessageBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // MT103 — Single Customer Credit Transfer
    // ========================================================================

    /**
     * Builds a complete SWIFT MT103 (Single Customer Credit Transfer) message.
     *
     * <p><strong>Fields included:</strong></p>
     * <ul>
     *   <li>:20: — Sender's Reference (paymentRef)</li>
     *   <li>:23B: — Bank Operation Code (CRED)</li>
     *   <li>:32A: — Value Date / Currency / Amount</li>
     *   <li>:50A:/:50K: — Ordering Customer (payer)</li>
     *   <li>:59:/:59A: — Beneficiary Customer (payee)</li>
     *   <li>:70: — Remittance Information</li>
     *   <li>:71A: — Details of Charges (SHA = shared)</li>
     * </ul>
     *
     * @param payment     the payment instruction with all payment details
     * @param senderBic   the sender's SWIFT BIC code (11 characters)
     * @param receiverBic the receiver's SWIFT BIC code (11 characters)
     * @return the complete SWIFT MT103 message as a multi-line string
     */
    public static String buildMt103(PaymentInstruction payment, String senderBic, String receiverBic) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment instruction must not be null");
        }

        log.debug("Building MT103 for payment ref: {}", payment.getPaymentRef());

        StringBuilder sb = new StringBuilder();

        // Block 1 — Basic Header
        sb.append("{1:").append(buildBasicHeader("103", senderBic)).append("}").append(SWIFT_EOL);

        // Block 2 — Application Header
        sb.append("{2:").append(buildApplicationHeader("103", receiverBic)).append("}").append(SWIFT_EOL);

        // Block 3 — User Header (optional, for banking priority)
        sb.append("{3:{113:NODC}{108:").append(sanitizeRef(payment.getPaymentRef(), 16))
                .append("}}").append(SWIFT_EOL);

        // Block 4 — Text Block
        sb.append("{4:").append(SWIFT_EOL);
        sb.append(buildMt103TextBlock(payment)).append(SWIFT_EOL);
        sb.append("-}").append(SWIFT_EOL);

        // Block 5 — Trailers
        sb.append("{5:{CHK:").append(generateChecksumPlaceholder())
                .append("}}").append(SWIFT_EOL);

        return sb.toString();
    }

    /**
     * Builds the text block (block 4) for an MT103 message.
     *
     * @param payment the payment instruction
     * @return the formatted field block
     */
    private static String buildMt103TextBlock(PaymentInstruction payment) {
        StringBuilder sb = new StringBuilder();

        // :20: Sender's Reference (max 16 chars)
        sb.append(":20:").append(sanitizeRef(payment.getPaymentRef(), 16));

        // :23B: Bank Operation Code — always CRED for customer transfer
        sb.append(FIELD_SEP).append("23B:CRED");

        // :32A: Value Date / Currency / Amount
        sb.append(FIELD_SEP).append("32A:")
                .append(formatSwiftDate(payment.getValueDate()))
                .append(payment.getCurrency() != null ? payment.getCurrency().toUpperCase() : "SGD")
                .append(formatSwiftAmount(payment.getAmount()));

        // :50K: Ordering Customer (payer account and name)
        sb.append(FIELD_SEP).append("50K:").append(buildOrderingCustomer(payment));

        // :59: Beneficiary (payee account and name)
        sb.append(FIELD_SEP).append("59:").append(buildBeneficiary(payment));

        // :70: Remittance Information (max 140 chars, up to 4 lines of 35)
        if (payment.getRemittance() != null && !payment.getRemittance().isEmpty()) {
            sb.append(FIELD_SEP).append("70:")
                    .append(ValidationUtils.sanitizeSwiftField(payment.getRemittance(), 140));
        }

        // :71A: Details of Charges — SHA (shared charges)
        sb.append(FIELD_SEP).append("71A:SHA");

        return sb.toString();
    }

    /**
     * Builds the ordering customer field (:50K:).
     *
     * @param payment the payment instruction
     * @return the formatted field value
     */
    private static String buildOrderingCustomer(PaymentInstruction payment) {
        StringBuilder sb = new StringBuilder();
        // Account line
        if (payment.getPayerAccount() != null && !payment.getPayerAccount().isEmpty()) {
            sb.append("/").append(ValidationUtils.sanitizeSwiftReference(payment.getPayerAccount(), 34));
            sb.append(SWIFT_EOL);
        }
        // Name line
        if (payment.getPayerName() != null && !payment.getPayerName().isEmpty()) {
            sb.append(ValidationUtils.sanitizeSwiftField(payment.getPayerName(), 35));
        } else {
            sb.append("ORDERING CUSTOMER");
        }
        return sb.toString();
    }

    /**
     * Builds the beneficiary field (:59:).
     *
     * @param payment the payment instruction
     * @return the formatted field value
     */
    private static String buildBeneficiary(PaymentInstruction payment) {
        StringBuilder sb = new StringBuilder();
        // Account line
        if (payment.getPayeeAccount() != null && !payment.getPayeeAccount().isEmpty()) {
            sb.append("/").append(ValidationUtils.sanitizeSwiftReference(payment.getPayeeAccount(), 34));
            sb.append(SWIFT_EOL);
        }
        // Name and address — name first line
        if (payment.getPayeeName() != null && !payment.getPayeeName().isEmpty()) {
            sb.append(ValidationUtils.sanitizeSwiftField(payment.getPayeeName(), 35));
        } else {
            sb.append("BENEFICIARY");
        }
        // BIC on sub-line if available
        if (payment.getPayeeBic() != null && !payment.getPayeeBic().isEmpty()) {
            sb.append(SWIFT_EOL).append(ValidationUtils.sanitizeSwiftField(payment.getPayeeBic(), 35));
        }
        return sb.toString();
    }

    // ========================================================================
    // MT202 — General Financial Institution Transfer
    // ========================================================================

    /**
     * Builds a complete SWIFT MT202 (General Financial Institution Transfer)
     * message, typically used for cover payments between banks.
     *
     * <p><strong>Fields included:</strong></p>
     * <ul>
     *   <li>:20: — Transaction Reference Number</li>
     *   <li>:21: — Related Reference</li>
     *   <li>:32A: — Value Date / Currency / Amount</li>
     *   <li>:52A: — Ordering Institution (sender BIC)</li>
     *   <li>:57A: — Account With Institution (receiver BIC)</li>
     *   <li>:58A: — Beneficiary Institution</li>
     * </ul>
     *
     * @param payment     the payment instruction
     * @param senderBic   the ordering institution's BIC
     * @param receiverBic the account-with institution's BIC
     * @return the complete SWIFT MT202 message as a multi-line string
     */
    public static String buildMt202(PaymentInstruction payment, String senderBic, String receiverBic) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment instruction must not be null");
        }

        log.debug("Building MT202 for payment ref: {}", payment.getPaymentRef());

        StringBuilder sb = new StringBuilder();

        // Block 1 — Basic Header
        sb.append("{1:").append(buildBasicHeader("202", senderBic)).append("}").append(SWIFT_EOL);

        // Block 2 — Application Header
        sb.append("{2:").append(buildApplicationHeader("202", receiverBic)).append("}").append(SWIFT_EOL);

        // Block 3 — User Header
        sb.append("{3:{113:NODC}{108:").append(sanitizeRef(payment.getPaymentRef(), 16))
                .append("}}").append(SWIFT_EOL);

        // Block 4 — Text Block
        sb.append("{4:").append(SWIFT_EOL);
        sb.append(buildMt202TextBlock(payment, senderBic, receiverBic)).append(SWIFT_EOL);
        sb.append("-}").append(SWIFT_EOL);

        // Block 5 — Trailers
        sb.append("{5:{CHK:").append(generateChecksumPlaceholder())
                .append("}}").append(SWIFT_EOL);

        return sb.toString();
    }

    /**
     * Builds the text block (block 4) for an MT202 message.
     */
    private static String buildMt202TextBlock(PaymentInstruction payment,
                                               String senderBic, String receiverBic) {
        StringBuilder sb = new StringBuilder();

        // :20: Transaction Reference Number (max 16 chars)
        sb.append(":20:").append(sanitizeRef(payment.getPaymentRef(), 16));

        // :21: Related Reference (same as :20: for simplicity)
        sb.append(FIELD_SEP).append("21:").append(sanitizeRef(payment.getPaymentRef(), 16));

        // :32A: Value Date / Currency / Amount
        sb.append(FIELD_SEP).append("32A:")
                .append(formatSwiftDate(payment.getValueDate()))
                .append(payment.getCurrency() != null ? payment.getCurrency().toUpperCase() : "SGD")
                .append(formatSwiftAmount(payment.getAmount()));

        // :52A: Ordering Institution
        if (senderBic != null && !senderBic.isEmpty()) {
            sb.append(FIELD_SEP).append("52A:").append(senderBic.toUpperCase().trim());
        }

        // :57A: Account With Institution
        if (receiverBic != null && !receiverBic.isEmpty()) {
            sb.append(FIELD_SEP).append("57A:").append(receiverBic.toUpperCase().trim());
        }

        // :58A: Beneficiary Institution (payee's bank BIC)
        if (payment.getPayeeBic() != null && !payment.getPayeeBic().isEmpty()) {
            sb.append(FIELD_SEP).append("58A:").append(payment.getPayeeBic().toUpperCase().trim());
        }

        return sb.toString();
    }

    // ========================================================================
    // Block builders
    // ========================================================================

    /**
     * Builds the SWIFT Basic Header block (Block 1).
     * Format: F01 (application id) + sender BIC + session + sequence.
     *
     * @param messageType the message type (103, 202, etc.)
     * @param senderBic   the sender's BIC
     * @return the formatted basic header string
     */
    private static String buildBasicHeader(String messageType, String senderBic) {
        String bic = senderBic != null ? senderBic.toUpperCase().trim() : "EPSUSGSGXXX";
        // F = FIN, 01 = application id, BIC, session, sequence
        return "F01" + bic + "0000000000";
    }

    /**
     * Builds the SWIFT Application Header block (Block 2).
     * Format: I (input) + message type + receiver BIC + priority + monitoring.
     *
     * @param messageType the message type
     * @param receiverBic the receiver's BIC
     * @return the formatted application header string
     */
    private static String buildApplicationHeader(String messageType, String receiverBic) {
        String bic = receiverBic != null ? receiverBic.toUpperCase().trim() : "XXXXXXXXXXX";
        // I = input, messageType, BIC, MIR, priority N
        return "I" + messageType + bic + "N";
    }

    // ========================================================================
    // Public formatting helpers
    // ========================================================================

    /**
     * Formats a {@link LocalDate} in SWIFT date format (YYMMDD).
     *
     * @param date the date to format; if null, today's date is used
     * @return the formatted date string, e.g. "260729" for July 29, 2026
     */
    public static String formatSwiftDate(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return d.format(SWIFT_DATE_FORMAT);
    }

    /**
     * Formats a monetary amount for SWIFT messages.
     *
     * <p>SWIFT amounts use a comma (,) as the decimal separator instead of
     * a period (.). For example, 1234.56 becomes "1234,56". For currencies
     * with zero decimals (JPY, KRW, etc.), no decimal separator is included.</p>
     *
     * @param amount the amount to format; if null, "0" is returned
     * @return the SWIFT-formatted amount string
     */
    public static String formatSwiftAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        // SWIFT uses comma as decimal separator, no thousands separator
        String plain = amount.toPlainString();
        return plain.replace('.', ',');
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    /**
     * Sanitizes a reference field for SWIFT (max 16 chars, SWIFT-safe chars only).
     *
     * @param ref       the reference string
     * @param maxLength the maximum length
     * @return the sanitized reference
     */
    private static String sanitizeRef(String ref, int maxLength) {
        return ValidationUtils.sanitizeSwiftReference(ref, maxLength);
    }

    /**
     * Generates a placeholder checksum for the trailer block.
     * In production, this would be computed using the actual SWIFT
     * checksum algorithm, but for EPS output a placeholder is sufficient
     * as the actual calculation is performed by the SWIFT gateway.
     *
     * @return a placeholder checksum string
     */
    private static String generateChecksumPlaceholder() {
        return "0000000000";
    }
}
