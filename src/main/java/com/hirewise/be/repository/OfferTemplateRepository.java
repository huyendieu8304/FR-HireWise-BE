package com.hirewise.be.repository;

import com.hirewise.be.domain.OfferTemplate;
import com.hirewise.be.domain.OfferTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Read access to the offer letter template catalog (UC-36 step 2). */
public interface OfferTemplateRepository extends JpaRepository<OfferTemplate, Long> {

    /**
     * Templates selectable in UC-36's dropdown: the company-wide ones
     * ({@code department_id IS NULL}) plus those belonging to the department
     * of the job being offered. Written as an explicit query rather than a
     * derived method name because the OR-over-two-conditions shape makes the
     * generated name unreadable.
     *
     * @param status       template status to include, normally {@code ACTIVE}
     * @param departmentId department of the job being offered; {@code null}
     *                     yields the company-wide templates only
     * @return matching templates ordered by name then newest version first
     */
    @Query("""
            SELECT t FROM OfferTemplate t
            WHERE t.status = :status
              AND (t.department IS NULL OR t.department.id = :departmentId)
            ORDER BY t.name ASC, t.version DESC
            """)
    List<OfferTemplate> findSelectable(@Param("status") OfferTemplateStatus status,
                                        @Param("departmentId") Long departmentId);
}
