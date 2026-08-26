package com.hirewise.be.repository;

import com.hirewise.be.domain.EmailTemplate;
import com.hirewise.be.domain.EmailTemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * UC-09: persistence layer for {@link EmailTemplate} entities.
 */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    /** BR-EMAILTPL-01: code is unique system-wide. Used on create. */
    boolean existsByCode(String code);

    /**
     * BR-EMAILTPL-01: excludes self so unchanged codes are not flagged as duplicates on update.
     */
    boolean existsByCodeAndIdNot(String code, Long id);

    /** Finds template by code (e.g. EM-01). */
    java.util.Optional<EmailTemplate> findByCode(String code);

    /** Finds template by code and status. */
    java.util.Optional<EmailTemplate> findByCodeAndStatus(String code, EmailTemplateStatus status);

    /** Returns templates linked to the given stage id (used during delete validation). */
    List<EmailTemplate> findByPipelineStageId(Long pipelineStageId);

    Page<EmailTemplate> findAll(Pageable pageable);
}