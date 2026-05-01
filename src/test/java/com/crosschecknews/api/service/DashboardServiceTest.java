package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.dashboard.DashboardChartsResponse;
import com.crosschecknews.api.dto.dashboard.DashboardSummaryResponse;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock ArticleRepository articleRepository;
    @Mock TopicRepository topicRepository;
    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock PipelineStepHistoryRepository pipelineStepHistoryRepository;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(articleRepository, topicRepository,
                pipelineRunRepository, pipelineStepHistoryRepository);
    }

    // ── getSummary ──────────────────────────────────────────────────────────

    @Test
    void articles와_topics가_선택_날짜_범위로_집계된다() {
        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(30L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(7L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(0L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getTotalArticles()).isEqualTo(30L);
        assertThat(result.getTotalTopics()).isEqualTo(7L);
    }

    @Test
    void todayCollectedArticles가_오늘_날짜_범위로_집계된다() {
        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(5L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(3L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(0L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getTodayCollectedArticles()).isEqualTo(5L);
    }

    @Test
    void date가_전달되면_해당_날짜_범위로_집계된다() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 2, 0, 0);

        given(articleRepository.countByFetchedAtBetween(start, end)).willReturn(6L);
        given(topicRepository.countByCreatedAtBetween(start, end)).willReturn(4L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), eq(start), eq(end))).willReturn(1L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary(date);

        assertThat(result.getTodayCollectedArticles()).isEqualTo(6L);
        assertThat(result.getTotalArticles()).isEqualTo(6L);
        assertThat(result.getTotalTopics()).isEqualTo(4L);
        verify(articleRepository).countByFetchedAtBetween(start, end);
        verify(topicRepository).countByCreatedAtBetween(start, end);
        verify(pipelineStepHistoryRepository, times(2)).countByStatusInAndStartedAtBetween(any(), eq(start), eq(end));
    }

    @Test
    void failedJobs가_FAILED_상태_기준으로_집계된다() {
        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(0L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(0L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(3L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getFailedJobs()).isEqualTo(3L);
    }

    @Test
    void lastCollectedAt이_가장_최근_완료된_run의_finishedAt으로_설정된다() {
        LocalDateTime expectedTime = LocalDateTime.of(2026, 4, 29, 10, 30);
        PipelineRun recentRun = PipelineRun.builder()
                .id(1L).status(PipelineStatus.SUCCESS)
                .fetchedCount(10).savedCount(8).clusteredCount(2).summarizedCount(2)
                .startedAt(expectedTime.minusMinutes(5))
                .finishedAt(expectedTime)
                .build();

        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(0L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(0L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(0L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of(recentRun));
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getLastCollectedAt()).isEqualTo(expectedTime);
    }

    @Test
    void finishedAt이_null인_run만_있으면_lastCollectedAt은_null이다() {
        PipelineRun runningRun = PipelineRun.builder()
                .id(1L).status(PipelineStatus.RUNNING)
                .fetchedCount(0).savedCount(0).clusteredCount(0).summarizedCount(0)
                .startedAt(LocalDateTime.now())
                .build();

        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(0L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(0L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(0L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of(runningRun));
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getLastCollectedAt()).isNull();
    }

    @Test
    void recentRuns가_PipelineStepHistory에서_DTO로_매핑된다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 29, 9, 0);
        LocalDateTime endTime   = LocalDateTime.of(2026, 4, 29, 9, 5);

        PipelineRun run = PipelineRun.builder()
                .id(42L).status(PipelineStatus.SUCCESS)
                .fetchedCount(12).savedCount(7).clusteredCount(2).summarizedCount(2)
                .startedAt(startTime).finishedAt(endTime)
                .build();

        PipelineStepHistory history = PipelineStepHistory.builder()
                .id(10L).pipelineRun(run)
                .step(PipelineStep.RSS_COLLECT).status(PipelineStatus.SUCCESS)
                .targetType("PUBLISHER_FEED").targetName("Fox News")
                .processedCount(4).message("완료")
                .startedAt(startTime).finishedAt(endTime)
                .build();

        given(articleRepository.countByFetchedAtBetween(any(), any())).willReturn(0L);
        given(topicRepository.countByCreatedAtBetween(any(), any())).willReturn(0L);
        given(pipelineStepHistoryRepository.countByStatusInAndStartedAtBetween(any(), any(), any())).willReturn(0L);
        given(pipelineRunRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of());
        given(pipelineStepHistoryRepository.findTop10ByOrderByStartedAtDesc()).willReturn(List.of(history));

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.getRecentRuns()).hasSize(1);
        DashboardSummaryResponse.PipelineRunItem item = result.getRecentRuns().get(0);
        assertThat(item.getId()).isEqualTo(10L);
        assertThat(item.getPipelineRunId()).isEqualTo(42L);
        assertThat(item.getStep()).isEqualTo(PipelineStep.RSS_COLLECT);
        assertThat(item.getStatus()).isEqualTo(PipelineStatus.SUCCESS);
        assertThat(item.getFetchedCount()).isEqualTo(12);
        assertThat(item.getTargetName()).isEqualTo("Fox News");
        assertThat(item.getStartedAt()).isEqualTo(startTime);
    }

    // ── getCharts ───────────────────────────────────────────────────────────

    @Test
    void articlesByPublisher가_publisher별_집계로_매핑된다() {
        given(articleRepository.countArticlesByPublisher()).willReturn(List.of(
                new Object[]{"Fox News", 4L},
                new Object[]{"NYT", 3L}
        ));
        given(topicRepository.countTopicsByCountry()).willReturn(List.of());
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(any(), any(), any())).willReturn(0L);

        DashboardChartsResponse result = service.getCharts(LocalDate.now());

        assertThat(result.getArticlesByPublisher()).hasSize(2);
        assertThat(result.getArticlesByPublisher().get(0).getName()).isEqualTo("Fox News");
        assertThat(result.getArticlesByPublisher().get(0).getCount()).isEqualTo(4L);
        assertThat(result.getArticlesByPublisher().get(1).getName()).isEqualTo("NYT");
        assertThat(result.getArticlesByPublisher().get(1).getCount()).isEqualTo(3L);
    }

    @Test
    void topicsByCountry가_Country_enum을_name으로_변환하여_매핑된다() {
        given(articleRepository.countArticlesByPublisher()).willReturn(List.of());
        given(topicRepository.countTopicsByCountry()).willReturn(List.of(
                new Object[]{Country.US, 5L},
                new Object[]{Country.KR, 2L}
        ));
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(any(), any(), any())).willReturn(0L);

        DashboardChartsResponse result = service.getCharts(LocalDate.now());

        assertThat(result.getTopicsByCountry()).hasSize(2);
        assertThat(result.getTopicsByCountry().get(0).getName()).isEqualTo("US");
        assertThat(result.getTopicsByCountry().get(0).getCount()).isEqualTo(5L);
        assertThat(result.getTopicsByCountry().get(1).getName()).isEqualTo("KR");
    }

    @Test
    void pipelineStatusCounts가_모든_PipelineStatus_값을_포함한다() {
        given(articleRepository.countArticlesByPublisher()).willReturn(List.of());
        given(topicRepository.countTopicsByCountry()).willReturn(List.of());
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(eq(PipelineStatus.RUNNING), any(), any())).willReturn(1L);
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(eq(PipelineStatus.SUCCESS), any(), any())).willReturn(10L);
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(eq(PipelineStatus.PARTIAL_FAILED), any(), any())).willReturn(3L);
        given(pipelineStepHistoryRepository.countByStatusAndStartedAtBetween(eq(PipelineStatus.FAILED), any(), any())).willReturn(2L);

        DashboardChartsResponse result = service.getCharts(LocalDate.now());

        assertThat(result.getPipelineStatusCounts()).hasSize(PipelineStatus.values().length);
        assertThat(result.getPipelineStatusCounts())
                .extracting(DashboardChartsResponse.NamedCount::getName)
                .containsExactly("RUNNING", "SUCCESS", "PARTIAL_FAILED", "FAILED");
        assertThat(result.getPipelineStatusCounts())
                .extracting(DashboardChartsResponse.NamedCount::getCount)
                .containsExactly(1L, 10L, 3L, 2L);
    }
}
