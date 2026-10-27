package com.eps.entity.enums;

/**
 * Payment instruction status lifecycle.
 *
 * <pre>
 * INIT -> APPR -> WAIT -> SENT -> ACK -> SETT
 *                \-> RJCT
 * Any state -> CANC
 * </pre>
 */
public enum PaymentStatus {
    /** Instruction created, pending approval */
    INIT,
    /** Approved by maker-checker, awaiting submission */
    APPR,
    /** Submitted and waiting for bank/network response */
    WAIT,
    /** Successfully sent to payment network or bank */
    SENT,
    /** Acknowledged by recipient network */
    ACK,
    /** Funds settled */
    SETT,
    /** Rejected during approval or submission */
    RJCT,
    /** Cancelled by operator before settlement */
    CANC
}
