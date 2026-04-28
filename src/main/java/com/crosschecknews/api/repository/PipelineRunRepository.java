package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
    long countByStatus(PipelineStatus status);

    List<PipelineRun> findTop10ByOrderByStartedAtDesc();
}
