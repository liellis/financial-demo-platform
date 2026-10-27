package com.eps.util;

import java.util.List;
import java.util.Optional;

/**
 * Implements the Levenshtein distance (edit distance) algorithm for fuzzy
 * string matching, modeled after AS/400 EPSNUM {@code CalcLevenshtein}.
 *
 * <p>The Levenshtein distance between two strings is the minimum number of
 * single-character edits (insertions, deletions, or substitutions) required
 * to change one string into the other. This is useful in payment processing
 * for fuzzy matching of payee names, bank names, and remittance narratives
 * when exact string matching is insufficient.</p>
 *
 * <p>All methods handle null inputs gracefully: a null string is treated
 * as an empty string, yielding the maximum possible distance.</p>
 */
public final class LevenshteinDistance {

    /** Private constructor to prevent instantiation. */
    private LevenshteinDistance() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================================================
    // Core algorithm
    // ========================================================================

    /**
     * Calculates the raw Levenshtein edit distance between two strings.
     * Uses the classic dynamic programming approach with O(n*m) time
     * and O(min(n,m)) space complexity (optimized to use two rows).
     *
     * <p>Null or empty inputs are handled gracefully:
     * if either string is null or empty, the length of the other
     * string is returned.</p>
     *
     * @param s1 the first string; null is treated as empty
     * @param s2 the second string; null is treated as empty
     * @return the minimum number of edits required, always non-negative
     */
    public static int calculate(String s1, String s2) {
        String a = s1 != null ? s1 : "";
        String b = s2 != null ? s2 : "";

        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        // Ensure 'a' is the shorter string for space optimization
        if (a.length() > b.length()) {
            String temp = a;
            a = b;
            b = temp;
        }

        int m = a.length();
        int n = b.length();

        // Use two-row optimization
        int[] prevRow = new int[m + 1];
        int[] currRow = new int[m + 1];

        // Initialize first row
        for (int i = 0; i <= m; i++) {
            prevRow[i] = i;
        }

        for (int j = 1; j <= n; j++) {
            currRow[0] = j;
            for (int i = 1; i <= m; i++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                currRow[i] = Math.min(
                        Math.min(
                                prevRow[i] + 1,      // deletion
                                currRow[i - 1] + 1   // insertion
                        ),
                        prevRow[i - 1] + cost        // substitution
                );
            }
            // Swap rows
            int[] tempRow = prevRow;
            prevRow = currRow;
            currRow = tempRow;
        }

        return prevRow[m];
    }

    /**
     * Calculates the Levenshtein distance with optional case-insensitive comparison.
     *
     * @param s1             the first string; null is treated as empty
     * @param s2             the second string; null is treated as empty
     * @param caseInsensitive if true, both strings are lowercased before comparison
     * @return the minimum number of edits required
     */
    public static int calculate(String s1, String s2, boolean caseInsensitive) {
        if (caseInsensitive) {
            String a = s1 != null ? s1.toLowerCase() : "";
            String b = s2 != null ? s2.toLowerCase() : "";
            return calculate(a, b);
        }
        return calculate(s1, s2);
    }

    // ========================================================================
    // Similarity
    // ========================================================================

    /**
     * Computes a normalized similarity score between 0.0 and 1.0.
     *
     * <p>The formula is:
     * <pre>similarity = 1 - (distance / maxLength)</pre>
     * A result of 1.0 means the strings are identical; 0.0 means they
     * share no characters in common.</p>
     *
     * <p>If both strings are null or empty, 1.0 is returned.</p>
     *
     * @param s1 the first string; null is treated as empty
     * @param s2 the second string; null is treated as empty
     * @return a similarity score between 0.0 and 1.0 inclusive
     */
    public static double similarity(String s1, String s2) {
        return similarity(s1, s2, false);
    }

    /**
     * Computes a normalized similarity score with optional case-insensitive comparison.
     *
     * @param s1             the first string; null is treated as empty
     * @param s2             the second string; null is treated as empty
     * @param caseInsensitive if true, both strings are lowercased before comparison
     * @return a similarity score between 0.0 and 1.0 inclusive
     */
    public static double similarity(String s1, String s2, boolean caseInsensitive) {
        int distance = calculate(s1, s2, caseInsensitive);
        int maxLen = Math.max(
                s1 != null ? s1.length() : 0,
                s2 != null ? s2.length() : 0
        );
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - ((double) distance / maxLen);
    }

    // ========================================================================
    // Matching helpers
    // ========================================================================

    /**
     * Finds the closest matching string from a list of candidates based on
     * Levenshtein distance. Returns the candidate with the highest similarity
     * score. Case-insensitive by default.
     *
     * @param target     the target string to match against
     * @param candidates the list of candidate strings
     * @return an {@link Optional} containing the closest match, or
     *         {@link Optional#empty()} if the candidates list is null or empty
     */
    public static Optional<String> findClosestMatch(String target, List<String> candidates) {
        return findClosestMatch(target, candidates, true);
    }

    /**
     * Finds the closest matching string from a list of candidates with
     * configurable case sensitivity.
     *
     * @param target          the target string to match against
     * @param candidates      the list of candidate strings
     * @param caseInsensitive if true, comparison is case-insensitive
     * @return an {@link Optional} containing the closest match, or
     *         {@link Optional#empty()} if the candidates list is null or empty
     */
    public static Optional<String> findClosestMatch(String target, List<String> candidates,
                                                     boolean caseInsensitive) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        String bestMatch = null;
        double bestScore = 0.0;

        for (String candidate : candidates) {
            double score = similarity(target, candidate, caseInsensitive);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
            // Early exit on perfect match
            if (score >= 1.0) {
                break;
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * Checks whether two strings are a fuzzy match based on a similarity
     * threshold. Useful for matching payee names or bank names where small
     * typographical variations are expected.
     *
     * @param s1        the first string; null is treated as empty
     * @param s2        the second string; null is treated as empty
     * @param threshold the minimum similarity required (0.0 to 1.0)
     * @return true if similarity >= threshold
     * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
     */
    public static boolean isFuzzyMatch(String s1, String s2, double threshold) {
        return isFuzzyMatch(s1, s2, threshold, false);
    }

    /**
     * Checks whether two strings are a fuzzy match with optional case-insensitive
     * comparison.
     *
     * @param s1              the first string; null is treated as empty
     * @param s2              the second string; null is treated as empty
     * @param threshold       the minimum similarity required (0.0 to 1.0)
     * @param caseInsensitive if true, comparison is case-insensitive
     * @return true if similarity >= threshold
     * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
     */
    public static boolean isFuzzyMatch(String s1, String s2, double threshold,
                                        boolean caseInsensitive) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        double score = similarity(s1, s2, caseInsensitive);
        return score >= threshold;
    }
}
