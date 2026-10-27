package com.eps.entity.enums;

/**
 * Reconciliation match status codes.
 */
public enum MatchStatus {
    /** Matched - payment and statement line agree on amount */
    MA,
    /** Break - difference exceeds tolerance, requires investigation */
    BO,
    /** Extra item on one side only - no counterpart in other system */
    EO,
    /** Amount mismatch - both sides present but amounts differ beyond small-value tolerance */
    AM
}
