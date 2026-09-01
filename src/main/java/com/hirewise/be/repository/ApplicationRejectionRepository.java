package com.hirewise.be.repository;

import com.hirewise.be.domain.ApplicationRejection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ApplicationRejection} entities.
 */
public interface ApplicationRejectionRepository extends JpaRepository<ApplicationRejection, Long> {

    /**
     * UC-20: the rejection record for one Application, if it was ever
     * rejected (BR-REJ-03: at most one, ever) - with {@code reason} and
     * {@code rejectedBy} eagerly fetched for the Applicant Card detail view.
     *
     * @param applicationId id of the application
     * @return the application's rejection record, if any
     */
    @Query("""
            SELECT r FROM ApplicationRejection r
            JOIN FETCH r.reason
            LEFT JOIN FETCH r.rejectedBy
            WHERE r.application.id = :applicationId
            """)
    Optional<ApplicationRejection> findByApplication_Id(@Param("applicationId") UUID applicationId);
}
