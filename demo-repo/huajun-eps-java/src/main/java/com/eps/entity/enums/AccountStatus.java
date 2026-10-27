package com.eps.entity.enums;

/**
 * Internal account operational status.
 */
public enum AccountStatus {
    /** Active - account can receive/send payments */
    A,
    /** Dormant - no activity for a period, limited operations */
    D,
    /** Closed - account permanently deactivated */
    C
}
