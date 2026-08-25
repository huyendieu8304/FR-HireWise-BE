package com.hirewise.be.repository;

import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ApplicationFile} entities.
 */
public interface ApplicationFileRepository extends JpaRepository<ApplicationFile, Long> {

    /**
     * UC-17 AF-01: files of a given role already attached to an
     * Application, so a repeat applicant's previous CV(s) can be demoted
     * ({@code isPrimary=false}) when a new one is uploaded.
     *
     * @param applicationId application id
     * @param fileRole      the role to filter by, e.g. {@link ApplicationFileRole#CV}
     * @return the matching application files, most recent first is not guaranteed
     */
    List<ApplicationFile> findByApplication_IdAndFileRole(UUID applicationId, ApplicationFileRole fileRole);
}
