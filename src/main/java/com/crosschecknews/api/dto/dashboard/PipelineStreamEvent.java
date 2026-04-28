package com.crosschecknews.api.dto.dashboard;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PipelineStreamEvent {
    private Long pipelineRunId;
    private PipelineStep step;
    private PipelineStatus status;
    private String message;
    private int progress;
    private String targetName;
    private String errorMessage;
    private LocalDateTime emittedAt;
}
