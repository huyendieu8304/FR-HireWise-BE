package com.hirewise.be.service;

import com.hirewise.be.domain.AuditLog;
import com.hirewise.be.repository.AuditLogRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Appends {@code audit_logs} rows for sensitive operations. First used by
 * UC-07/UC-08 (Cloud Storage connect/reconnect/disconnect); intentionally
 * generic (entityType/entityId as plain strings) so later use cases - role
 * assignment, Job publish, CV access, etc. - can reuse it instead of each
 * rolling its own logging.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class AuditLogService {

    AuditLogRepository auditLogRepository;
    Clock clock;

    /**
     * Appends one audit trail row. Runs in its own transaction so a
     * disconnect/connect action that is otherwise successful can never be
     * rolled back purely because of a problem writing the audit row (the
     * business action's own transaction already committed by the time this
     * is typically called at the end of a service method; when called
     * within the same transaction, REQUIRES_NEW would be needed instead -
     * see the {@code propagation} note below if that changes).
     *
     * @param actorUserId internal id of the user who performed the action;
     *                    {@code null} for a system/automation action
     * @param action      short action code, e.g. {@code "CLOUD_STORAGE_CONNECTED"}
     * @param entityType  the affected table, e.g. {@code "storage_connections"}
     * @param entityId    the affected row's id (as text - ids are BIGINT or UUID
     *                    depending on the entity)
     */
    @Transactional
    public void record(Long actorUserId, String action, String entityType, String entityId) {
        Instant now = Instant.now(clock);
        AuditLog auditLog = AuditLog.builder()
                .actorUserId(actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .createdAt(now)
                .build();
        auditLogRepository.save(auditLog);
    }
}
