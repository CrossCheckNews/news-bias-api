package com.crosschecknews.api.dto.dashboard;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PipelineStepHistoryResponse {
    private Long id;
    private Long pipelineRunId;
    private PipelineStep step;
    private PipelineStatus status;
    private String targetType;
    private String targetName;
    private int processedCount;
    private int successCount;
    private int failedCount;
    private String errorType;
    private String errorMessage;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
