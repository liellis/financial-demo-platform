package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalTime;

/**
 * Bank configuration entity, mapped to table EPSBNK.
 * Stores configuration settings for each bank that the EPS system
 * interacts with, including routing codes for various payment
 * channels, settlement cutoffs, and contact information.
 */
@Entity
@Table(name = "EPSBNK")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankConfig {

    /** Unique bank code, length 10 */
    @Id
    @Column(name = "BANK_CODE", length = 10, nullable = false)
    private String bankCode;

    /** Full name of the bank, length 100 */
    @Column(name = "BANK_NAME", length = 100)
    private String bankName;

    /** BIC / SWIFT code for international routing, length 11 */
    @Column(name = "BIC_CODE", length = 11)
    private String bicCode;

    /** ISO 3166-1 alpha-3 country code, length 3 */
    @Column(name = "COUNTRY_CODE", length = 3)
    private String countryCode;

    /** Bank code used for GIRO payment channel, length 10 */
    @Column(name = "GIRO_BANK_CODE", length = 10)
    private String giroBankCode;

    /** Bank code used for FAST payment channel, length 10 */
    @Column(name = "FAST_BANK_CODE", length = 10)
    private String fastBankCode;

    /** Routing code for MEPS (MAS Electronic Payment System), length 10 */
    @Column(name = "MEPS_ROUTING_CODE", length = 10)
    private String mepsRoutingCode;

    /** Whether SWIFT messaging is enabled for this bank */
    @Column(name = "SWIFT_ENABLED")
    private Boolean swiftEnabled;

    /**
     * Operational status of the bank configuration.
     * ACTIVE, INACTIVE, SUSPENDED.
     */
    @Column(name = "STATUS", length = 10)
    private String status;

    /** Daily cutoff time for settlement processing through this bank */
    @Column(name = "SETTLEMENT_CUTOFF")
    private LocalTime settlementCutoff;

    /** Primary contact email address for the bank, length 100 */
    @Column(name = "CONTACT_EMAIL", length = 100)
    private String contactEmail;

    /** Primary contact phone number for the bank, length 20 */
    @Column(name = "CONTACT_PHONE", length = 20)
    private String contactPhone;
}
