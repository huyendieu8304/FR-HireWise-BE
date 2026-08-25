package com.hirewise.be.repository;

import com.hirewise.be.domain.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ApplicationStageHistory} entities - the immutable
 * stage-change log. UC-17 only ever appends the very first ("New") event
 * here; reading/rolling back this log belongs to later use cases (Kanban
 * board, stage rollback).
 */
public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, Long> {
}
