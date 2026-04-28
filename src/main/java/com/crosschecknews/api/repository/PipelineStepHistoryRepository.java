package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStepHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineStepHistoryRepository extends JpaRepository<PipelineStepHistory, Long> {
    long countByStatus(PipelineStatus status);

    @EntityGraph(attributePaths = "pipelineRun")
    List<PipelineStepHistory> findTop10ByOrderByStartedAtDesc();
}
