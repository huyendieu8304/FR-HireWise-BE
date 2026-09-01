package com.hirewise.be.repository;

import com.hirewise.be.domain.RejectionReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link RejectionReason} entities (UC-29 catalog, BR-REJ-01).
 */
public interface RejectionReasonRepository extends JpaRepository<RejectionReason, Long> {

    /**
     * UC-29 step 1: the reject dropdown's choices - only reasons still open
     * for NEW rejections; an inactive one stays queryable by id (for past
     * {@link com.hirewise.be.domain.ApplicationRejection} rows) but never
     * resurfaces here.
     *
     * @return active reasons ordered by label for a stable, alphabetical dropdown
     */
    List<RejectionReason> findByActiveTrueOrderByLabelAsc();
}
