package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One append-only audit trail entry for a sensitive operation (UC-07/UC-08:
 * connect/reconnect/disconnect Cloud Storage; later reused by role
 * assignment, Job publish, etc.). {@link #actorUserId} is a plain id
 * (not a {@code @ManyToOne User}) on purpose - writing an audit row must
 * never require loading the full user entity first, and must still work
 * for a {@code null} actor (a system/automation action).
 */
@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "before_json", columnDefinition = "text")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "text")
    private String afterJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
