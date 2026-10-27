package com.eps.entity.enums;

/**
 * Payment channel / network supported by EPS.
 */
public enum ChannelType {
    /** Fast And Secure Transfers - Singapore instant payment */
    FAST,
    /** MAS Electronic Payment System - large-value SGD transfers */
    MEPS,
    /** PayNow proxy-based instant payment */
    PAYNOW,
    /** Interbank GIRO - batch low-value clearing */
    GIRO,
    /** SWIFT cross-border wire transfer */
    SWIFT
}
