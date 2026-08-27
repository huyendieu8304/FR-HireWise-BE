package com.hirewise.be.repository;

import com.hirewise.be.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AuditLog} entities. Deliberately minimal - callers
 * only ever append rows through {@code service.AuditLogService}; there is no
 * update/delete path (audit logs are append-only).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
