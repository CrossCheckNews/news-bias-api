package com.crosschecknews.api.service;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PipelineStepHistoryService {

    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;

    public Page<PipelineStepHistoryResponse> getHistories(LocalDate date, int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
        Page<PipelineStepHistory> histories = (date != null)
                ? pipelineStepHistoryRepository.findByStartedAtBetween(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable)
                : pipelineStepHistoryRepository.findAll(pageable);
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
                .errorType(h.getErrorType())
                .errorMessage(h.getErrorMessage())
                .message(h.getMessage())
                .startedAt(h.getStartedAt())
                .finishedAt(h.getFinishedAt())
                .build();
    }
}
