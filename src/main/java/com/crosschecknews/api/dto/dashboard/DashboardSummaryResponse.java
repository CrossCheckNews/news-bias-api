package com.crosschecknews.api.dto.dashboard;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DashboardSummaryResponse {
    private long totalArticles;
    private long totalTopics;
    private long todayCollectedArticles;
    private long successJobs;
    private long failedJobs;
    private LocalDateTime lastCollectedAt;
    private List<PipelineRunItem> recentRuns;

    @Getter
    @Builder
    public static class PipelineRunItem {
        private Long id;
        private Long pipelineRunId;
        private PipelineStep step;
        private PipelineStatus status;
        private int fetchedCount;
        private int savedCount;
        private int clusteredCount;
        private int summarizedCount;
        private int processedCount;
        private int successCount;
        private int failedCount;
        private String targetType;
        private String targetName;
        private String errorType;
        private String errorMessage;
        private String message;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }
}
