package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStepHistory;
import com.crosschecknews.api.dto.dashboard.DashboardChartsResponse;
import com.crosschecknews.api.dto.dashboard.DashboardSummaryResponse;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;

    public DashboardSummaryResponse getSummary() {
        return getSummary(null);
    }

    public DashboardSummaryResponse getSummary(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime todayStart = targetDate.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long successJobs = pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(
                List.of(PipelineStatus.SUCCESS, PipelineStatus.PARTIAL_FAILED), todayStart, tomorrowStart
        );

        long failedJobs = pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(
                List.of(PipelineStatus.FAILED, PipelineStatus.PARTIAL_FAILED), todayStart, tomorrowStart
        );
        long articleCount = articleRepository.countByFetchedAtBetween(todayStart, tomorrowStart);
        long topicCount = topicRepository.countByCreatedAtBetween(todayStart, tomorrowStart);

        LocalDateTime lastCollectedAt = pipelineRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(PipelineRun::getFinishedAt)
                .filter(finishedAt -> finishedAt != null)
                .findFirst()
                .orElse(null);

        return DashboardSummaryResponse.builder()
                .totalArticles(articleCount)
                .totalTopics(topicCount)
                .todayCollectedArticles(articleCount)
                .successJobs(successJobs)
                .failedJobs(failedJobs)
                .lastCollectedAt(lastCollectedAt)
                .recentRuns(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc().stream()
                        .map(this::toRunItem)
                        .toList())
                .build();
    }

    private DashboardSummaryResponse.PipelineRunItem toRunItem(PipelineStepHistory history) {
        PipelineRun run = history.getPipelineRun();
        return DashboardSummaryResponse.PipelineRunItem.builder()
                .id(history.getId())
                .pipelineRunId(run.getId())
                .step(history.getStep())
                .status(history.getStatus())
                .fetchedCount(run.getFetchedCount())
                .savedCount(run.getSavedCount())
                .clusteredCount(run.getClusteredCount())
                .summarizedCount(run.getSummarizedCount())
                .processedCount(history.getProcessedCount())
                .successCount(history.getSuccessCount())
                .failedCount(history.getFailedCount())
                .targetType(history.getTargetType())
                .targetName(history.getTargetName())
                .errorType(history.getErrorType())
                .errorMessage(history.getErrorMessage())
                .message(history.getMessage())
                .startedAt(history.getStartedAt())
                .finishedAt(history.getFinishedAt())
                .build();
    }

    public DashboardChartsResponse getCharts(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime from = targetDate.atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        return DashboardChartsResponse.builder()
                .articlesByPublisher(articleRepository.countArticlesByPublisher().stream()
                        .map(row -> namedCount((String) row[0], (Long) row[1]))
                        .toList())
                .topicsByCountry(topicRepository.countTopicsByCountry().stream()
                        .map(row -> namedCount(((Country) row[0]).name(), (Long) row[1]))
                        .toList())
                .pipelineStatusCounts(Arrays.stream(PipelineStatus.values())
                        .map(status -> namedCount(status.name(),
                                pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(status, from, to)))
                        .toList())
                .build();
    }

    private DashboardChartsResponse.NamedCount namedCount(String name, long count) {
        return DashboardChartsResponse.NamedCount.builder()
                .name(name)
                .count(count)
                .build();
    }
}
