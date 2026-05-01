package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStepHistory;
import com.crosschecknews.api.dto.dashboard.PipelineStepHistoryResponse;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PipelineStepHistoryService {

    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;

    public Page<PipelineStepHistoryResponse> getHistories(LocalDate date, List<PipelineStatus> statuses, int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
        boolean hasStatuses = statuses != null && !statuses.isEmpty();
        Page<PipelineStepHistory> histories;
        if (date != null && hasStatuses) {
            histories = pipelineStepHistoryRepository.findByStartedAtBetweenAndStatusIn(
                    date.atStartOfDay(), date.plusDays(1).atStartOfDay(), statuses, pageable);
        } else if (date != null) {
            histories = pipelineStepHistoryRepository.findByStartedAtBetween(
                    date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
        } else if (hasStatuses) {
            histories = pipelineStepHistoryRepository.findByStatusIn(statuses, pageable);
        } else {
            histories = pipelineStepHistoryRepository.findAll(pageable);
        }
        return histories.map(this::toResponse);
    }

    private PipelineStepHistoryResponse toResponse(PipelineStepHistory h) {
        return PipelineStepHistoryResponse.builder()
                .id(h.getId())
                .pipelineRunId(h.getPipelineRun().getId())
                .step(h.getStep())
                .status(h.getStatus())
                .targetType(h.getTargetType())
                .targetName(h.getTargetName())
                .processedCount(h.getProcessedCount())
                .successCount(h.getSuccessCount())
                .failedCount(h.getFailedCount())
                .errorType(h.getErrorType())
                .errorMessage(h.getErrorMessage())
                .message(h.getMessage())
                .startedAt(h.getStartedAt())
                .finishedAt(h.getFinishedAt())
                .build();
    }
}
