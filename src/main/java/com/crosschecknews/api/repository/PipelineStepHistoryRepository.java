package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStepHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PipelineStepHistoryRepository extends JpaRepository<PipelineStepHistory, Long> {
    long countByStatus(PipelineStatus status);

    @EntityGraph(attributePaths = "pipelineRun")
    List<PipelineStepHistory> findTop10ByOrderByStartedAtDesc();

    @Override
    @EntityGraph(attributePaths = "pipelineRun")
    Page<PipelineStepHistory> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "pipelineRun")
    Page<PipelineStepHistory> findByStartedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByStatusInAndStartedAtBetween(Collection<PipelineStatus> statuses, LocalDateTime from, LocalDateTime to);

    long countByStatusAndStartedAtBetween(PipelineStatus status, LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = "pipelineRun")
    Page<PipelineStepHistory> findByStatusIn(Collection<PipelineStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "pipelineRun")
    Page<PipelineStepHistory> findByStartedAtBetweenAndStatus(LocalDateTime from, LocalDateTime to, PipelineStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "pipelineRun")
    Page<PipelineStepHistory> findByStartedAtBetweenAndStatusIn(LocalDateTime from, LocalDateTime to, Collection<PipelineStatus> statuses, Pageable pageable);
}
