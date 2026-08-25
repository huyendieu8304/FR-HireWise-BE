package com.hirewise.be.repository;

import com.hirewise.be.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Candidate} entities.
 */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    /**
     * BR-APPLY-02: identity lookup used by UC-17 to decide whether an
     * applicant is brand new or already has a {@code candidates} row to
     * reuse. Case-insensitive so {@code Jane@Mail.com} and
     * {@code jane@mail.com} are treated as the same person.
     *
     * @param primaryEmail the email the candidate applied with
     * @return the existing candidate row, if one exists for this email
     */
    Optional<Candidate> findByPrimaryEmailIgnoreCase(String primaryEmail);
}
