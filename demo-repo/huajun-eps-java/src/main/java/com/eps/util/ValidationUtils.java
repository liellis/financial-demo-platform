package com.eps.util;

import java.util.regex.Pattern;

/**
 * Validation utility class for payment-related data fields, modeled
 * after AS/400 EPSCNV validation routines.
 *
 * <p>Provides validation for Singapore-specific identifiers (NRIC, UEN),
 * international banking codes (SWIFT BIC, IBAN), account numbers,
 * and SWIFT field sanitization.</p>
 *
 * <p>All methods are static and return boolean results. This class
 * is not intended to be instantiated.</p>
 */
public final class ValidationUtils {

    // ========================================================================
    // NRIC constants and weights
    // ========================================================================

    /** Valid NRIC prefix letters. */
    private static final String NRIC_PREFIXES = "STFG";

    /** Weight table for the NRIC checksum calculation (7 positions). */
    private static final int[] NRIC_WEIGHTS = {2, 7, 6, 5, 4, 3, 2};

    /**
     * Checksum letter table for NRIC types S and T (MOD 11).
     * Index 0 maps to value 0, index 10 maps to value 10.
     */
    private static final String NRIC_CHECKSUM_ST = "JZIHGFEDCBA";

    /**
     * Checksum letter table for NRIC types F and G (MOD 11).
     */
    private static final String NRIC_CHECKSUM_FG = "XWUTRQPNMLK";

    // ========================================================================
    // Regex patterns
    // ========================================================================

    /** Singapore NRIC format: S/T/F/G + 7 digits + checksum letter. */
    private static final Pattern NRIC_PATTERN =
            Pattern.compile("^[STFG]\\d{7}[A-Z]$");

    /** SWIFT BIC format: 6 letters (bank code) + 2 letters/digits (country) + optional 3 alphanumeric (branch). */
    private static final Pattern SWIFT_BIC_PATTERN =
            Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");

    /** IBAN basic format: 2 letters + 2 digits + up to 30 alphanumeric characters. */
    private static final Pattern IBAN_PATTERN =
            Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{1,30}$");

    /** Characters that are invalid in SWIFT field content. */
    private static final Pattern SWIFT_INVALID_CHARS =
            Pattern.compile("[^A-Za-z0-9/\\-?:().,'+\\s]");

    /** Characters invalid in SWIFT reference fields (stricter: no special chars except hyphen and slash). */
    private static final Pattern SWIFT_REF_INVALID_CHARS =
            Pattern.compile("[^A-Za-z0-9/\\-]");

    // ========================================================================
    // Constructor
    // ========================================================================

    /** Private constructor to prevent instantiation. */
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // NRIC validation
    // ========================================================================

    /**
     * Validates a Singapore NRIC (National Registration Identity Card) number.
     *
     * <p>The NRIC format is: a prefix letter (S, T, F, or G), followed by
     * 7 digits, followed by a checksum letter. The checksum is calculated
     * using a weighted MOD 11 algorithm.</p>
     *
     * <p><strong>Prefix meanings:</strong></p>
     * <ul>
     *   <li>S — Singapore citizen or permanent resident born before 2000</li>
     *   <li>T — Singapore citizen or permanent resident born in/after 2000</li>
     *   <li>F — Foreigner holding a long-term pass issued before 2000</li>
     *   <li>G — Foreigner holding a long-term pass issued in/after 2000</li>
     * </ul>
     *
     * @param nric the NRIC string to validate; may be null or empty
     * @return true if the NRIC is valid
     */
    public static boolean isValidNric(String nric) {
        if (nric == null || nric.isEmpty()) {
            return false;
        }

        String cleaned = nric.trim().toUpperCase();

        // Basic format check
        if (!NRIC_PATTERN.matcher(cleaned).matches()) {
            return false;
        }

        // Validate checksum
        char providedChecksum = cleaned.charAt(8);
        char expectedChecksum = nricChecksum(cleaned);

        return providedChecksum == expectedChecksum;
    }

    /**
     * Calculates the expected NRIC checksum letter.
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Multiply each digit by its position weight (2,7,6,5,4,3,2)</li>
     *   <li>Sum the weighted values</li>
     *   <li>For types S/T: add 4 to the sum (offset)</li>
     *   <li>Take MOD 11 of the result</li>
     *   <li>Look up the checksum letter from the appropriate table</li>
     * </ol>
     *
     * @param nric the formatted NRIC string (assumed already pattern-validated)
     * @return the expected checksum character
     */
    private static char nricChecksum(String nric) {
        char prefix = nric.charAt(0);
        int sum = 0;

        // Calculate weighted sum of the 7 digits
        for (int i = 0; i < 7; i++) {
            int digit = Character.getNumericValue(nric.charAt(i + 1));
            sum += digit * NRIC_WEIGHTS[i];
        }

        // S/T types have an offset of 4
        if (prefix == 'S' || prefix == 'T') {
            sum += 4;
        }

        int remainder = sum % 11;

        // Select the appropriate checksum table
        if (prefix == 'S' || prefix == 'T') {
            return NRIC_CHECKSUM_ST.charAt(remainder);
        } else {
            // F or G
            return NRIC_CHECKSUM_FG.charAt(remainder);
        }
    }

    // ========================================================================
    // UEN validation
    // ========================================================================

    /**
     * Validates a Singapore UEN (Unique Entity Number).
     *
     * <p>Singapore UENs come in three formats depending on the entity type:</p>
     * <ul>
     *   <li><strong>Format A (Businesses registered with ACRA):</strong>
     *       9 or 10 digits followed by a checksum letter, e.g. {@code 201234567A}</li>
     *   <li><strong>Format B (Local companies registered with ACRA):</strong>
     *       4-digit year + 5-6 digit number + checksum letter, e.g. {@code 202012345A}</li>
     *   <li><strong>Format C (Other entities):</strong>
     *       T + 2-character entity type + PQ + 4-digit year + 4-digit number + checksum,
     *       e.g. {@code T08PQ2012A}</li>
     * </ul>
     *
     * @param uen the UEN string to validate; may be null or empty
     * @return true if the UEN is valid
     */
    public static boolean isValidUen(String uen) {
        if (uen == null || uen.isEmpty()) {
            return false;
        }

        String cleaned = uen.trim().toUpperCase();

        // Format A: 9-10 digits + letter (Businesses)
        if (cleaned.matches("^\\d{9,10}[A-Z]$")) {
            return true;
        }

        // Format B: 4 digits (year) + 5-6 digits + letter (Local Companies)
        if (cleaned.matches("^\\d{4}\\d{5,6}[A-Z]$") && cleaned.length() <= 11) {
            return true;
        }

        // Format C: T + 2 chars + PQ + 4 digits + 4 alphanum + checksum (Other Entities)
        if (cleaned.matches("^T\\d{2}[A-Z]{2}\\d{4}[A-Z0-9]{4}[A-Z]$") && cleaned.length() <= 14) {
            return true;
        }

        return false;
    }

    // ========================================================================
    // SWIFT BIC validation
    // ========================================================================

    /**
     * Validates a SWIFT/BIC (Bank Identifier Code).
     *
     * <p>A valid BIC is either 8 or 11 characters long:
     * {@code AAAABBCCDDD} where:</p>
     * <ul>
     *   <li>AAAA — 4-letter bank code</li>
     *   <li>BB — 2-letter ISO country code</li>
     *   <li>CC — 2-character location code (letters or digits)</li>
     *   <li>DDD — optional 3-character branch code (letters or digits)</li>
     * </ul>
     *
     * @param bic the BIC string to validate; may be null or empty
     * @return true if the BIC is valid (8 or 11 characters matching the pattern)
     */
    public static boolean isValidSwiftBic(String bic) {
        if (bic == null || bic.isEmpty()) {
            return false;
        }

        String cleaned = bic.trim().toUpperCase();

        // Length must be exactly 8 or 11
        if (cleaned.length() != 8 && cleaned.length() != 11) {
            return false;
        }

        return SWIFT_BIC_PATTERN.matcher(cleaned).matches();
    }

    // ========================================================================
    // IBAN validation
    // ========================================================================

    /**
     * Performs a basic IBAN (International Bank Account Number) format check.
     *
     * <p>This is a structural validation only — it checks the pattern
     * (2-letter country code + 2 check digits + up to 30 alphanumeric characters)
     * and length constraints but does NOT validate the MOD 97 checksum.
     * For full IBAN validation including the checksum, use a dedicated
     * IBAN library such as Apache Commons Validator.</p>
     *
     * @param iban the IBAN string to validate; may be null or empty
     * @return true if the IBAN matches the basic format
     */
    public static boolean isValidIban(String iban) {
        if (iban == null || iban.isEmpty()) {
            return false;
        }

        String cleaned = iban.trim().toUpperCase().replaceAll("\\s+", "");

        // Remove any spaces for checking
        return IBAN_PATTERN.matcher(cleaned).matches();
    }

    // ========================================================================
    // Account number validation
    // ========================================================================

    /**
     * Performs a basic length and format check on a bank account number
     * for the specified bank.
     *
     * <p>This method validates that the account number is not null/empty
     * and falls within a reasonable length range (1-30 characters) for
     * the given bank code. For bank-specific validation rules, extend
     * this method with additional logic.</p>
     *
     * @param account  the account number to validate; may be null
     * @param bankCode the bank code for context-specific rules; may be null
     * @return true if the account number passes basic validation
     */
    public static boolean isValidAccountNumber(String account, String bankCode) {
        if (account == null || account.trim().isEmpty()) {
            return false;
        }

        String cleaned = account.trim();

        // Basic length check: 1 to 30 characters
        if (cleaned.length() < 1 || cleaned.length() > 30) {
            return false;
        }

        // Only alphanumeric characters, hyphens, and spaces
        if (!cleaned.matches("^[A-Za-z0-9\\-\\s]+$")) {
            return false;
        }

        return true;
    }

    // ========================================================================
    // SWIFT field sanitization
    // ========================================================================

    /**
     * Sanitizes a string value for use in SWIFT message fields.
     *
     * <p>SWIFT MT messages only allow a restricted character set:
     * letters (A-Z, a-z), digits (0-9), forward slash (/), hyphen (-),
     * question mark (?), colon (:), parentheses, period (.), comma (,),
     * apostrophe ('), plus (+), and space. All other characters are
     * removed. The value is then truncated to the specified maximum length.</p>
     *
     * @param value     the value to sanitize; null returns empty string
     * @param maxLength the maximum allowed length after sanitization
     * @return the sanitized and truncated string
     */
    public static String sanitizeSwiftField(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String sanitized = SWIFT_INVALID_CHARS.matcher(value).replaceAll("");
        // Also replace newlines with spaces (SWIFT doesn't support multi-line fields typically)
        sanitized = sanitized.replace('\n', ' ').replace('\r', ' ');

        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    /**
     * Sanitizes a string value for SWIFT reference fields (stricter character set).
     * Reference fields only allow letters, digits, hyphens, and forward slashes.
     *
     * @param value     the value to sanitize; null returns empty string
     * @param maxLength the maximum allowed length
     * @return the sanitized and truncated string
     */
    public static String sanitizeSwiftReference(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String sanitized = SWIFT_REF_INVALID_CHARS.matcher(value).replaceAll("");

        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }
}
