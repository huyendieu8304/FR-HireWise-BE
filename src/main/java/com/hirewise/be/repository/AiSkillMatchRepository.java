package com.hirewise.be.repository;

import com.hirewise.be.domain.AiSkillMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository for {@link AiSkillMatch} (UC-21 breakdown rows). */
public interface AiSkillMatchRepository extends JpaRepository<AiSkillMatch, Long> {

    List<AiSkillMatch> findByRun_Id(Long runId);
}
