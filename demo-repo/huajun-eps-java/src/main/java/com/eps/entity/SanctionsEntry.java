package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sanctions screening list entry, mapped to table EPSSANC.
 * Stores individuals and entities from various sanctions lists
 * (UN, OFSI, MAS, EU) for screening payment parties before
 * transactions are processed.
 */
@Entity
@Table(name = "EPSSANC")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsEntry {

    /** Unique sanctions entry identifier, length 20 */
    @Id
    @Column(name = "ENTRY_ID", length = 20, nullable = false)
    private String entryId;

    /** Full name of the sanctioned individual or entity, length 200 */
    @Column(name = "ENTITY_NAME", length = 200)
    private String entityName;

    /**
     * Type of sanctioned party.
     * INDIVIDUAL = Natural person, ENTITY = Organisation/corporation.
     */
    @Column(name = "ENTITY_TYPE", length = 10)
    private String entityType;

    /** National ID, passport number, or registration number, length 30 */
    @Column(name = "ID_NUMBER", length = 30)
    private String idNumber;

    /** ISO 3166-1 alpha-3 country code of nationality, length 3 */
    @Column(name = "NATIONALITY", length = 3)
    private String nationality;

    /**
     * Source sanctions list.
     * UN = United Nations, OFSI = UK Office of Financial Sanctions Implementation,
     * MAS = Monetary Authority of Singapore, EU = European Union.
     */
    @Column(name = "LIST_SOURCE", length = 20)
    private String listSource;

    /** Date when the entity was added to the sanctions list */
    @Column(name = "DATE_LISTED")
    private LocalDate dateListed;

    /**
     * Risk level assigned to this entry.
     * HIGH = Immediate block, MEDIUM = Enhanced due diligence, LOW = Record only.
     */
    @Column(name = "RISK_LEVEL", length = 10)
    private String riskLevel;

    /**
     * Current status of this sanctions entry.
     * ACTIVE = Currently sanctioned, DELISTED = Removed from list.
     */
    @Column(name = "STATUS", length = 10)
    private String status;

    /** Additional remarks or details, length 500 */
    @Column(name = "REMARKS", length = 500)
    private String remarks;

    /** Timestamp when this entry was loaded into the system */
    @Column(name = "LOAD_DATE")
    private LocalDateTime loadDate;

    /** User ID who loaded this entry, length 20 */
    @Column(name = "LOAD_USER", length = 20)
    private String loadUser;
}
