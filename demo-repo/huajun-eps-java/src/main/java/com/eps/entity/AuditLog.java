package com.eps.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit log entity, mapped to table EPSAUD.
 * Captures all data changes and significant actions across the EPS system
 * for compliance, traceability, and forensic analysis. Each record captures
 * who made the change, what was changed, and when.
 */
@Entity
@Table(name = "EPSAUD")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    /** Auto-generated unique log identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID", nullable = false)
    private Long logId;

    /** User ID of the person who performed the action, length 20 */
    @Column(name = "USER_ID", length = 20)
    private String userId;

    /** Timestamp when the action occurred */
    @Column(name = "TIMESTAMP")
    private LocalDateTime timestamp;

    /** Name of the table or entity that was modified, length 30 */
    @Column(name = "TABLE_NAME", length = 30)
    private String tableName;

    /** Primary key value of the affected record, length 50 */
    @Column(name = "RECORD_KEY", length = 50)
    private String recordKey;

    /**
     * Type of action performed.
     * INSERT, UPDATE, DELETE, APPROVE, REJECT, SUBMIT, etc.
     */
    @Column(name = "ACTION", length = 20)
    private String action;

    /** Previous field values before the change (JSON or delimited), length 4000 */
    @Column(name = "OLD_VALUE", length = 4000)
    private String oldValue;

    /** New field values after the change (JSON or delimited), length 4000 */
    @Column(name = "NEW_VALUE", length = 4000)
    private String newValue;

    /** IP address of the client that initiated the action, length 45 */
    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    /** Session identifier for correlating actions within a user session, length 50 */
    @Column(name = "SESSION_ID", length = 50)
    private String sessionId;
}
