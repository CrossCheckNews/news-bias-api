package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.domain.PipelineStepHistory;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.crosschecknews.api.domain.PipelineStatus.FAILED;
import static com.crosschecknews.api.domain.PipelineStatus.PARTIAL_FAILED;
import static com.crosschecknews.api.domain.PipelineStatus.RUNNING;
import static com.crosschecknews.api.domain.PipelineStatus.SUCCESS;
import static com.crosschecknews.api.domain.PipelineStep.AI_SUMMARY;
import static com.crosschecknews.api.domain.PipelineStep.COMPLETED;
import static com.crosschecknews.api.domain.PipelineStep.TOPIC_CLUSTERING;

/**
 * RSS 수집 → 정규화/저장 → 클러스터링 → AI 요약 파이프라인 오케스트레이터.
 *
 * <p>각 단계는 기존 서비스에 위임하며, 단계별 실패는 격리해 나머지 흐름을 계속 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsIngestionPipelineService {

    private final ArticleSaveService    articleSaveService;
    private final TopicClusteringService topicClusteringService;
    private final AiSummaryService      aiSummaryService;
    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;
    private final PipelineEventPublisher eventPublisher;

    public PipelineResult run(PipelineRequest request) {
        int fromHours = (request != null) ? request.getFromHours() : 48;
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("파이프라인 시작 fromHours={}", fromHours);

        // PipelineRun 선행 생성 — 이후 모든 SSE 이벤트/이력에 runId 사용
        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .status(RUNNING)
                .fetchedCount(0).savedCount(0).clusteredCount(0).summarizedCount(0)
                .message("파이프라인 실행 중")
                .startedAt(startedAt)
                .build());
        Long runId = run.getId();

        // Stage 1~3: RSS 수집 + 기사 저장 (ArticleSaveService 내부에서 이벤트 발행)
        FetchAndSaveResult fetchAndSave = runFetchAndSave(runId);

        // Stage 4: 카테고리별 클러스터링
        eventPublisher.publish(runId, TOPIC_CLUSTERING, RUNNING, "유사 기사 토픽 클러스터링 중...", 50);
        ClusteringOutcome clusteringOutcome = runClustering(fromHours);
        List<ClusteringResult> clustering = clusteringOutcome.results();
        eventPublisher.publish(runId, TOPIC_CLUSTERING,
                clusteringOutcome.hadFailure() ? FAILED : SUCCESS,
                clusteringOutcome.hadFailure() ? "일부 카테고리 클러스터링 실패 (이후 단계 계속 진행)" : "토픽 클러스터링 완료",
                70);

        // Stage 5: AI 요약
        eventPublisher.publish(runId, AI_SUMMARY, RUNNING, "AI 요약 생성 중...", 75);
        SummarizeOutcome summarizeOutcome = runSummarize();
        List<SummarizeResponse> summaries = summarizeOutcome.results();
        eventPublisher.publish(runId, AI_SUMMARY,
                summarizeOutcome.hadFailure() ? FAILED : SUCCESS,
                summarizeOutcome.hadFailure() ? "AI 요약 실패" : "AI 요약 완료",
                95);

        LocalDateTime finishedAt = LocalDateTime.now();
        completeRun(run, fetchAndSave, clustering, summaries, finishedAt);
        saveStepHistory(run, fetchAndSave, clustering, summaries, startedAt, finishedAt);

        eventPublisher.publish(runId, COMPLETED, SUCCESS, "파이프라인 실행이 완료되었습니다.", 100);

        log.info("파이프라인 완료 runId={} fetched={} saved={} clusters={} summaries={}",
                runId, fetchAndSave.getFetchedCount(), fetchAndSave.getSavedCount(),
                clustering.stream().mapToInt(ClusteringResult::getClustersCreated).sum(),
                summaries.size());

        return PipelineResult.builder()
                .pipelineRunId(runId)
                .fetchAndSave(fetchAndSave)
                .clustering(clustering)
                .summaries(summaries)
                .executedAt(finishedAt)
                .build();
    }

    private void completeRun(PipelineRun run, FetchAndSaveResult fetchAndSave,
                              List<ClusteringResult> clustering, List<SummarizeResponse> summaries,
                              LocalDateTime finishedAt) {
        PipelineStatus status = statusFromCounts(fetchAndSave.getSavedCount(), fetchAndSave.getFailedCount());
        boolean hasFailure = status != SUCCESS;
        int clustersCreated = clustering.stream().mapToInt(ClusteringResult::getClustersCreated).sum();
        run.complete(
                status,
                fetchAndSave.getFetchedCount(),
                fetchAndSave.getSavedCount(),
                clustersCreated,
                summaries.size(),
                hasFailure ? "일부 단계 실패" : "파이프라인 정상 완료",
                hasFailure ? "실패한 단계 이력을 확인하세요." : null,
                finishedAt
        );
        pipelineRunRepository.save(run);
    }

    private void saveStepHistory(PipelineRun run, FetchAndSaveResult fetchAndSave,
                                  List<ClusteringResult> clustering, List<SummarizeResponse> summaries,
                                  LocalDateTime startedAt, LocalDateTime finishedAt) {
        saveFetchAndSaveSteps(run, fetchAndSave, startedAt);
        saveClusteringSteps(run, clustering);
        saveSummaryStep(run, summaries, finishedAt);
        saveCompletedStep(run, finishedAt);
    }

    private void saveFetchAndSaveSteps(PipelineRun run, FetchAndSaveResult result, LocalDateTime pipelineStartedAt) {
        LocalDateTime executedAt = result.getExecutedAt() != null ? result.getExecutedAt() : pipelineStartedAt;
        int articleSaveFailedCount = articleSaveFailedCount(result);

        if (result.getFeeds() == null || result.getFeeds().isEmpty()) {
            pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                    .pipelineRun(run)
                    .step(PipelineStep.RSS_COLLECT)
                    .status(statusFromCounts(result.getFetchedCount(), result.getFailedCount()))
                    .targetType("PIPELINE")
                    .targetName("ALL_FEEDS")
                    .processedCount(result.getFetchedCount())
                    .successCount(result.getFetchedCount())
                    .failedCount(result.getFailedCount())
                    .errorType(result.getFailedCount() > 0 ? "RSS_COLLECT_FAILED" : null)
                    .errorMessage(result.getFailedCount() > 0 ? "RSS 수집/저장 단계가 실패했습니다." : null)
                    .message(result.getFailedCount() > 0 ? "RSS 수집/저장 실패" : "RSS 수집/저장 완료")
                    .startedAt(pipelineStartedAt)
                    .finishedAt(executedAt)
                    .build());
            return;
        }

        pipelineStepHistoryRepository.saveAll(result.getFeeds().stream()
                .map(feed -> PipelineStepHistory.builder()
                        .pipelineRun(run)
                        .step(PipelineStep.RSS_COLLECT)
                        .status(feed.isCollectSuccess() ? PipelineStatus.SUCCESS : PipelineStatus.FAILED)
                        .targetType("PUBLISHER_FEED")
                        .targetName(feed.getPublisherName())
                        .processedCount(feed.getFetched())
                        .successCount(feed.isCollectSuccess() ? feed.getFetched() : 0)
                        .failedCount(feed.isCollectSuccess() ? 0 : 1)
                        .errorType(feed.isCollectSuccess() ? null : feed.getErrorType())
                        .errorMessage(feed.getErrorMessage())
                        .message(feed.isCollectSuccess() ? "RSS 피드 수집 완료" : "RSS 피드 수집 실패")
                        .startedAt(pipelineStartedAt)
                        .finishedAt(executedAt)
                        .build())
                .toList());

        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.ARTICLE_SAVE)
                .status(statusFromCounts(result.getSavedCount(), articleSaveFailedCount))
                .targetType("ARTICLE")
                .targetName("NORMALIZED_ARTICLES")
                .processedCount(result.getSavedCount() + articleSaveFailedCount)
                .successCount(result.getSavedCount())
                .failedCount(articleSaveFailedCount)
                .errorType(articleSaveFailedCount > 0 ? "ARTICLE_SAVE_PARTIAL_FAILED" : null)
                .errorMessage(articleSaveFailedCount > 0 ? articleSaveFailedCount + "건의 기사 저장에 실패했습니다." : null)
                .message(articleSaveFailedCount > 0
                        ? String.format("기사 저장 부분 실패 (저장 %d건, 실패 %d건)", result.getSavedCount(), articleSaveFailedCount)
                        : String.format("기사 저장 완료 (저장 %d건)", result.getSavedCount()))
                .startedAt(executedAt)
                .finishedAt(executedAt)
                .build());

        if (result.getValidationErrors() != null && !result.getValidationErrors().isEmpty()) {
            pipelineStepHistoryRepository.saveAll(result.getValidationErrors().stream()
                    .map(err -> PipelineStepHistory.builder()
                            .pipelineRun(run)
                            .step(PipelineStep.ARTICLE_SAVE)
                            .status(PipelineStatus.FAILED)
                            .targetType("ARTICLE")
                            .targetName(err.getTargetName())
                            .processedCount(0)
                            .successCount(0)
                            .failedCount(1)
                            .errorType(err.getErrorType())
                            .errorMessage(err.getErrorMessage())
                            .message("필수 필드 누락으로 기사 저장 건너뜀")
                            .startedAt(executedAt)
                            .finishedAt(executedAt)
                            .build())
                    .toList());
        }
    }

    private int articleSaveFailedCount(FetchAndSaveResult result) {
        if (result.getFeeds() == null || result.getFeeds().isEmpty()) {
            return result.getValidationErrors() == null ? 0 : result.getValidationErrors().size();
        }
        return result.getFeeds().stream()
                .filter(FetchAndSaveResult.FeedSummary::isCollectSuccess)
                .mapToInt(FetchAndSaveResult.FeedSummary::getFailed)
                .sum();
    }

    private PipelineStatus statusFromCounts(int successCount, int failedCount) {
        if (failedCount > 0 && successCount > 0) {
            return PARTIAL_FAILED;
        }
        if (failedCount > 0) {
            return FAILED;
        }
        return SUCCESS;
    }

    private void saveClusteringSteps(PipelineRun run, List<ClusteringResult> clustering) {
        pipelineStepHistoryRepository.saveAll(clustering.stream()
                .map(result -> PipelineStepHistory.builder()
                        .pipelineRun(run)
                        .step(PipelineStep.TOPIC_CLUSTERING)
                        .status(PipelineStatus.SUCCESS)
                        .targetType("ARTICLE_CATEGORY")
                        .targetName(result.getCategory().name())
                        .processedCount(result.getClustersCreated())
                        .successCount(result.getClustersCreated())
                        .failedCount(0)
                        .message("토픽 클러스터링 완료")
                        .startedAt(result.getExecutedAt())
                        .finishedAt(result.getExecutedAt())
                        .build())
                .toList());
    }

    private void saveSummaryStep(PipelineRun run, List<SummarizeResponse> summaries, LocalDateTime finishedAt) {
        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.AI_SUMMARY)
                .status(PipelineStatus.SUCCESS)
                .targetType("TOPIC")
                .targetName("ACTIVE_TOPICS")
                .processedCount(summaries.size())
                .successCount(summaries.size())
                .failedCount(0)
                .message("AI 요약 생성 완료")
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .build());
    }

    private void saveCompletedStep(PipelineRun run, LocalDateTime finishedAt) {
        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(COMPLETED)
                .status(PipelineStatus.SUCCESS)
                .targetType("PIPELINE")
                .targetName("FULL_PIPELINE")
                .processedCount(0)
                .successCount(0)
                .failedCount(0)
                .message("파이프라인 실행 완료")
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .build());
    }

    private FetchAndSaveResult runFetchAndSave(Long runId) {
        try {
            FetchAndSaveResult result = articleSaveService.fetchAndSaveAll(runId);
            log.info("[Stage 1~3] 수집/저장 완료 fetched={} saved={} duplicates={}",
                    result.getFetchedCount(), result.getSavedCount(), result.getDuplicateCount());
            return result;
        } catch (Exception e) {
            log.error("[Stage 1~3] 수집/저장 전체 실패: {}", e.getMessage(), e);
            return FetchAndSaveResult.builder()
                    .fetchedCount(0).savedCount(0).duplicateCount(0).failedCount(1)
                    .feeds(List.of()).executedAt(LocalDateTime.now()).build();
        }
    }

    private ClusteringOutcome runClustering(int fromHours) {
        List<ClusteringResult> results = new ArrayList<>();
        boolean hadFailure = false;

        for (ArticleCategory articleCategory : ArticleCategory.values()) {
            try {
                ClusteringRequest req = new ClusteringRequest(articleCategory, fromHours);
                ClusteringResult result = topicClusteringService.cluster(req);
                results.add(result);
                log.info("[Stage 4] 클러스터링 완료 category={} clusters={} linked={}",
                        articleCategory, result.getClustersCreated(), result.getLinkedArticleCount());
            } catch (Exception e) {
                log.error("[Stage 4] 클러스터링 실패 category={} cause={}", articleCategory, e.getMessage(), e);
                hadFailure = true;
            }
        }
        return new ClusteringOutcome(results, hadFailure);
    }

    private SummarizeOutcome runSummarize() {
        try {
            List<SummarizeResponse> results = aiSummaryService.summarizeAll();
            log.info("[Stage 5] AI 요약 완료 generated={}", results.size());
            return new SummarizeOutcome(results, false);
        } catch (Exception e) {
            log.error("[Stage 5] AI 요약 전체 실패: {}", e.getMessage(), e);
            return new SummarizeOutcome(List.of(), true);
        }
    }

    public PipelineRunLatestResponse getLatestRunDate() {
        String runDate = pipelineRunRepository.findTopByOrderByStartedAtDesc()
                .map(run -> run.getStartedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .orElse(null);
        return PipelineRunLatestResponse.builder()
                .runDate(runDate)
                .today(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .build();
    }

    private record ClusteringOutcome(List<ClusteringResult> results, boolean hadFailure) {}
    private record SummarizeOutcome(List<SummarizeResponse> results, boolean hadFailure) {}
}
