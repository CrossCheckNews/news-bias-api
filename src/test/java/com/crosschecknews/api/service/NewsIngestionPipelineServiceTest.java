package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsIngestionPipelineServiceTest {

    @Mock ArticleSaveService             articleSaveService;
    @Mock TopicClusteringService         topicClusteringService;
    @Mock AiSummaryService               aiSummaryService;
    @Mock PipelineRunRepository          pipelineRunRepository;
    @Mock PipelineStepHistoryRepository  pipelineStepHistoryRepository;
    @Mock PipelineEventPublisher         eventPublisher;

    @InjectMocks NewsIngestionPipelineService pipelineService;

    private PipelineRun runningRun;
    private FetchAndSaveResult successFetchResult;
    private ClusteringResult successClusterResult;
    private SummarizeResponse summarizeResponse;

    @BeforeEach
    void setUp() {
        runningRun = PipelineRun.builder()
                .id(1L)
                .status(PipelineStatus.RUNNING)
                .fetchedCount(0).savedCount(0).clusteredCount(0).summarizedCount(0)
                .message("파이프라인 실행 중")
                .startedAt(LocalDateTime.now())
                .build();

        successFetchResult = FetchAndSaveResult.builder()
                .fetchedCount(10).savedCount(8).duplicateCount(2).failedCount(0)
                .feeds(List.of()).executedAt(LocalDateTime.now())
                .build();

        successClusterResult = ClusteringResult.builder()
                .category(ArticleCategory.WORLD)
                .clustersCreated(3).linkedArticleCount(8)
                .scannedArticleCount(10)
                .topics(List.of())
                .executedAt(LocalDateTime.now())
                .build();

        summarizeResponse = SummarizeResponse.builder()
                .topicId(1L).title("테스트 토픽")
                .summary("요약 내용").aiModel("gemini")
                .generatedAt(LocalDateTime.now())
                .build();

        given(pipelineRunRepository.save(any())).willReturn(runningRun);
        given(pipelineStepHistoryRepository.saveAll(any())).willReturn(List.of());
        given(pipelineStepHistoryRepository.save(any())).willReturn(null);
    }

    // ── PipelineRun 생성 및 추적 ─────────────────────────────────────────────

    @Test
    void 파이프라인_시작_시_RUNNING_상태로_PipelineRun이_먼저_저장된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        ArgumentCaptor<PipelineRun> captor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void 파이프라인_완료_후_PipelineRun이_SUCCESS로_업데이트된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        // complete() 호출 후 두 번째 save 시 SUCCESS
        assertThat(runningRun.getStatus()).isEqualTo(PipelineStatus.SUCCESS);
        assertThat(runningRun.getFinishedAt()).isNotNull();
    }

    @Test
    void 파이프라인_결과에_pipelineRunId가_포함된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        PipelineResult result = pipelineService.run(null);

        assertThat(result.getPipelineRunId()).isEqualTo(1L);
    }

    @Test
    void fetchAndSave_호출_시_runId가_전달된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        verify(articleSaveService).fetchAndSaveAll(eq(1L));
    }

    // ── SSE 이벤트 발행 순서 ─────────────────────────────────────────────────

    @Test
    void TOPIC_CLUSTERING_RUNNING_후_SUCCESS_이벤트가_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.TOPIC_CLUSTERING),
                eq(PipelineStatus.RUNNING), anyString(), eq(50));
        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.TOPIC_CLUSTERING),
                eq(PipelineStatus.SUCCESS), anyString(), eq(70));
    }

    @Test
    void AI_SUMMARY_RUNNING_후_SUCCESS_이벤트가_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.AI_SUMMARY),
                eq(PipelineStatus.RUNNING), anyString(), eq(75));
        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.AI_SUMMARY),
                eq(PipelineStatus.SUCCESS), anyString(), eq(95));
    }

    @Test
    void COMPLETED_이벤트가_마지막에_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.COMPLETED),
                eq(PipelineStatus.SUCCESS), anyString(), eq(100));
    }

    // ── 실패 상태 반영 ──────────────────────────────────────────────────────

    @Test
    void 클러스터링_예외_발생_시_TOPIC_CLUSTERING_FAILED_이벤트가_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        willThrow(new RuntimeException("clustering error"))
                .given(topicClusteringService).cluster(any());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.TOPIC_CLUSTERING),
                eq(PipelineStatus.FAILED), anyString(), eq(70));
    }

    @Test
    void AI_요약_예외_발생_시_AI_SUMMARY_FAILED_이벤트가_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        willThrow(new RuntimeException("ai error")).given(aiSummaryService).summarizeAll();

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.AI_SUMMARY),
                eq(PipelineStatus.FAILED), anyString(), eq(95));
    }

    @Test
    void 클러스터링_실패_시에도_AI_요약과_COMPLETED_이벤트가_발행된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        willThrow(new RuntimeException("clustering error"))
                .given(topicClusteringService).cluster(any());
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null);

        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.AI_SUMMARY),
                eq(PipelineStatus.RUNNING), anyString(), eq(75));
        verify(eventPublisher).publish(eq(1L), eq(PipelineStep.COMPLETED),
                eq(PipelineStatus.SUCCESS), anyString(), eq(100));
    }

    // ── 결과 집계 ────────────────────────────────────────────────────────────

    @Test
    void 파이프라인_결과에_수집_및_클러스터링_정보가_포함된다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        PipelineResult result = pipelineService.run(null);

        assertThat(result.getFetchAndSave().getFetchedCount()).isEqualTo(10);
        assertThat(result.getClustering()).hasSize(1);
        assertThat(result.getSummaries()).hasSize(1);
        assertThat(result.getExecutedAt()).isNotNull();
    }

    @Test
    void fromHours_기본값은_48이다() {
        given(articleSaveService.fetchAndSaveAll(eq(1L))).willReturn(successFetchResult);
        given(topicClusteringService.cluster(any())).willReturn(successClusterResult);
        given(aiSummaryService.summarizeAll()).willReturn(List.of(summarizeResponse));

        pipelineService.run(null); // request = null → fromHours = 48

        ArgumentCaptor<ClusteringRequest> captor = ArgumentCaptor.forClass(ClusteringRequest.class);
        verify(topicClusteringService).cluster(captor.capture());
        assertThat(captor.getValue().getFromHours()).isEqualTo(48);
    }
}
