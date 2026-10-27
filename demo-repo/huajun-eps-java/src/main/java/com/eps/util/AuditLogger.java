package com.eps.util;

import com.eps.entity.AuditLog;
import com.eps.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Centralized audit logging utility for the Electronic Payment System (EPS).
 *
 * <p>This component provides a unified API for recording audit trail entries
 * across the system. Each significant user action or system event is persisted
 * to the {@code EPSLOG} table via {@link AuditLogRepository} and also written
 * to the SLF4J log for real-time monitoring.</p>
 *
 * <p>The audit logger captures:</p>
 * <ul>
 *   <li><strong>Who</strong> performed the action (userId)</li>
 *   <li><strong>What</strong> action was performed (actionType)</li>
 *   <li><strong>Where</strong> in the database (tableName, recordKey)</li>
 *   <li><strong>When</strong> the action occurred (timestamp)</li>
 *   <li><strong>What changed</strong> (oldValue vs newValue for updates)</li>
 * </ul>
 *
 * <p>This class is annotated with {@code @Component} for Spring injection.
 * Use {@code @Autowired} to obtain an instance.</p>
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final AuditLogRepository auditLogRepository;
    private final PaymentRefGenerator refGenerator;

    /**
     * Constructs an AuditLogger with the required repository and ID generator.
     *
     * @param auditLogRepository the JPA repository for persisting audit entries
     * @param refGenerator       the reference generator for creating unique log IDs
     */
    @Autowired
    public AuditLogger(AuditLogRepository auditLogRepository, PaymentRefGenerator refGenerator) {
        this.auditLogRepository = auditLogRepository;
        this.refGenerator = refGenerator;
    }

    // ========================================================================
    // General-purpose logging
    // ========================================================================

    /**
     * Logs a generic audit entry with full old/new value tracking.
     *
     * <p>This is the most general form and should be used when both the
     * before and after states are known, such as for field-level updates.</p>
     *
     * @param userId    the user ID performing the action (must not be null)
     * @param action    the action type (e.g. "CREATE", "APPROVE", "SUBMIT")
     * @param tableName the database table or logical entity name affected
     * @param recordKey the primary key of the affected record
     * @param oldValue  the previous value (null for inserts)
     * @param newValue  the new value (null for deletes)
     */
    public void log(String userId, String action, String tableName,
                    String recordKey, String oldValue, String newValue) {
        String logId = refGenerator.generate("AUD");

        AuditLog auditLog = AuditLog.builder()
                .logId(logId)
                .userId(userId)
                .actionType(action)
                .description(buildDescription(action, tableName, recordKey, oldValue, newValue))
                .tableName(tableName)
                .recordKey(recordKey)
                .timestamp(LocalDateTime.now())
                .program("EPS")
                .build();

        auditLogRepository.save(auditLog);

        log.info("AUDIT | {} | {} | {}.{} | old={} | new={}",
                userId, action, tableName, recordKey,
                truncateForLog(oldValue), truncateForLog(newValue));
    }

    // ========================================================================
    // Convenience methods for common audit scenarios
    // ========================================================================

    /**
     * Logs an INSERT operation. The old value is always null.
     *
     * @param userId    the user creating the record
     * @param tableName the target table name
     * @param recordKey the new record's primary key
     * @param newValue  a description of the inserted data
     */
    public void logInsert(String userId, String tableName, String recordKey, String newValue) {
        log(userId, "CREATE", tableName, recordKey, null, newValue);
    }

    /**
     * Logs an UPDATE operation with before/after values.
     *
     * @param userId    the user performing the update
     * @param tableName the target table name
     * @param recordKey the record's primary key
     * @param oldValue  the value before the update
     * @param newValue  the value after the update
     */
    public void logUpdate(String userId, String tableName, String recordKey,
                           String oldValue, String newValue) {
        log(userId, "UPDATE", tableName, recordKey, oldValue, newValue);
    }

    /**
     * Logs a DELETE operation. The new value is always null.
     *
     * @param userId    the user deleting the record
     * @param tableName the target table name
     * @param recordKey the record's primary key
     * @param oldValue  a description of the deleted data
     */
    public void logDelete(String userId, String tableName, String recordKey, String oldValue) {
        log(userId, "DELETE", tableName, recordKey, oldValue, null);
    }

    /**
     * Logs an approval action (maker-checker workflow).
     *
     * <p>In the EPS maker-checker model, approval is a distinct action
     * that transitions a payment from INIT/WAIT status to APPR/SENT status.
     * This convenience method records the approver's ID and any comment
     * they provided.</p>
     *
     * @param userId    the approver's user ID
     * @param tableName the table containing the approved record
     * @param recordKey the record's primary key
     * @param comment   the approver's comment (may be null)
     */
    public void logApproval(String userId, String tableName, String recordKey, String comment) {
        log(userId, "APPROVE", tableName, recordKey, null, comment);
    }

    /**
     * Logs a rejection action (maker-checker workflow).
     *
     * @param userId    the approver's user ID
     * @param tableName the table containing the rejected record
     * @param recordKey the record's primary key
     * @param reason    the rejection reason
     */
    public void logRejection(String userId, String tableName, String recordKey, String reason) {
        log(userId, "REJECT", tableName, recordKey, null, reason);
    }

    /**
     * Logs a system-level event (not associated with a specific user).
     *
     * @param action    the system action (e.g. "BATCH_START", "BATCH_END")
     * @param tableName the table or module affected
     * @param recordKey the record key or batch identifier
     * @param detail    additional detail about the system event
     */
    public void logSystem(String action, String tableName, String recordKey, String detail) {
        log("SYSTEM", action, tableName, recordKey, null, detail);
    }

    /**
     * Logs a payment submission to an external channel.
     *
     * @param userId     the user submitting the payment
     * @param paymentRef the payment reference
     * @param channel    the submission channel (FAST, MEPS, SWIFT, etc.)
     * @param bankRef    the bank-generated reference after submission
     */
    public void logSubmission(String userId, String paymentRef, String channel, String bankRef) {
        log(userId, "SUBMIT", "EPSPOH", paymentRef,
                "Channel: " + channel, "BankRef: " + bankRef);
    }

    /**
     * Logs a reconciliation match event.
     *
     * @param userId    the user or system performing the match
     * @param matchId   the match identifier
     * @param paymentRef the payment reference that was matched
     * @param statementRef the bank statement line reference
     */
    public void logReconMatch(String userId, String matchId, String paymentRef, String statementRef) {
        log(userId, "MATCH", "EPSREC", matchId,
                null, "Payment: " + paymentRef + " matched to Statement: " + statementRef);
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    /**
     * Builds a human-readable description from audit fields.
     */
    private String buildDescription(String action, String tableName, String recordKey,
                                     String oldValue, String newValue) {
        StringBuilder desc = new StringBuilder();
        desc.append(action).append(" on ").append(tableName);
        if (recordKey != null) {
            desc.append(" [").append(recordKey).append("]");
        }
        if (oldValue != null && newValue != null) {
            desc.append(" changed from '").append(truncateForLog(oldValue))
                    .append("' to '").append(truncateForLog(newValue)).append("'");
        } else if (newValue != null) {
            desc.append(" = '").append(truncateForLog(newValue)).append("'");
        } else if (oldValue != null) {
            desc.append(" was '").append(truncateForLog(oldValue)).append("'");
        }
        return desc.toString();
    }

    /**
     * Truncates a value for safe inclusion in log messages.
     *
     * @param value the value to truncate; null returns "null"
     * @return the truncated string (max 200 characters)
     */
    private String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= 200) {
            return value;
        }
        return value.substring(0, 197) + "...";
    }
}
