package com.hirewise.be.repository;

import com.hirewise.be.domain.JobPostingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link JobPostingChannel} entities (UC-31/UC-32).
 */
public interface JobPostingChannelRepository extends JpaRepository<JobPostingChannel, Long> {

    /**
     * UC-32: every channel this Job has been shared to, with the channel
     * itself fetched in the same query - the stats panel prints the channel
     * name on every row, so leaving it lazy would be a guaranteed N+1.
     *
     * @param jobPositionId id of the job position
     * @return one row per channel already shared to, in channel display order
     */
    @Query("""
            SELECT jpc FROM JobPostingChannel jpc
            JOIN FETCH jpc.channel c
            WHERE jpc.jobPosition.id = :jobPositionId
            ORDER BY c.displayOrder ASC
            """)
    List<JobPostingChannel> findByJobWithChannel(@Param("jobPositionId") UUID jobPositionId);

    /**
     * BR-POST-02: locates the single existing row for a (Job, Channel) pair so
     * a repeat share updates it in place instead of inserting a duplicate.
     *
     * @param jobPositionId id of the job position
     * @param code          the channel being shared to
     * @return the existing row, if this pair has ever been shared
     */
    Optional<JobPostingChannel> findByJobPosition_IdAndChannel_Code(
            UUID jobPositionId, PublishingChannelCode code);
}
