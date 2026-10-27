package com.eps.entity.enums;

/**
 * General Ledger voucher posting status.
 */
public enum PostStatus {
    /** Pending - voucher created but not yet posted to GL */
    P,
    /** Posted successfully to General Ledger */
    S,
    /** Error during posting, requires manual correction */
    E
}
