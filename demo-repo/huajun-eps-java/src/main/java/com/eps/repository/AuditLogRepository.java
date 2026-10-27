package com.eps.repository;

import com.eps.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AuditLog} entity.
 * <p>
 * Provides query methods for audit trail management. Audit logs
 * capture every create, update, and delete operation on critical
 * EPS entities, recording the acting user, timestamp, affected
 * table, and record identifier. This supports regulatory compliance,
 * forensic investigation, and operational troubleshooting.
 * </p>
 *
 * <p>
 * The entity's primary key is the {@code logId} field of type
 * {@link Long}, which is auto-generated via
 * {@code @GeneratedValue}. It inherits the {@code save} and
 * {@code saveAll} methods from {@link JpaRepository} for persisting
 * new audit records.
 * </p>
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit log entries for a specific user within a given
     * timestamp range. Supports user activity reviews and
     * investigation of suspicious or erroneous actions.
     *
     * @param userId the identifier of the user whose actions are
     *               being queried
     * @param start  the inclusive start of the timestamp range
     * @param end    the inclusive end of the timestamp range
     * @return list of audit log entries matching the user and time
     *         range, ordered by timestamp ascending, never
     *         {@code null}
     */
    List<AuditLog> findByUserIdAndTimestampBetween(String userId,
                                                    LocalDateTime start,
                                                    LocalDateTime end);

    /**
     * Find all audit log entries for a specific database table and
     * record key. Used to retrieve the complete change history of
     * a particular business entity, such as all modifications to a
     * specific payment instruction or account record.
     *
     * @param tableName the name of the database table (or logical
     *                  entity name) that was modified
     * @param recordKey the primary key or business identifier of
     *                  the affected record
     * @return list of audit log entries for the specified table and
     *         record, ordered by timestamp ascending, never
     *         {@code null}
     */
    List<AuditLog> findByTableNameAndRecordKey(String tableName, String recordKey);
}
