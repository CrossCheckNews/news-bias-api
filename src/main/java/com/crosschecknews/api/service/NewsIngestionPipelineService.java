package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Category;
import com.crosschecknews.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public PipelineResult run(PipelineRequest request) {
        int fromHours = (request != null) ? request.getFromHours() : 48;
        log.info("파이프라인 시작 fromHours={}", fromHours);

        // ── Stage 1~3: RSS 수집 + 정규화 + 저장 ─────────────────────────────
        FetchAndSaveResult saveResult = runFetchAndSave();

        // ── Stage 4: 카테고리별 클러스터링 ───────────────────────────────────
        ClusteringSummary clusteringSummary = runClustering(fromHours);

        // ── Stage 5: AI 요약 ─────────────────────────────────────────────────
        SummarySummary summarySummary = runSummarize();

        PipelineResult result = PipelineResult.builder()
                .fetchedCount(saveResult.getFetchedCount())
                .savedCount(saveResult.getSavedCount())
                .duplicateCount(saveResult.getDuplicateCount())
                .clustersCreated(clusteringSummary.clustersCreated)
                .linkedArticleCount(clusteringSummary.linkedArticleCount)
                .summariesGenerated(summarySummary.generated)
                .failedSources(failedSources(saveResult))
                .failedCategories(clusteringSummary.failedCategories)
                .summaryFailedCount(summarySummary.failed)
                .executedAt(LocalDateTime.now())
                .build();

        log.info("파이프라인 완료 fetched={} saved={} clusters={} summaries={}",
                result.getFetchedCount(), result.getSavedCount(),
                result.getClustersCreated(), result.getSummariesGenerated());

        return result;
    }

    // ── 내부 단계 ────────────────────────────────────────────────────────────

    private FetchAndSaveResult runFetchAndSave() {
        try {
            FetchAndSaveResult result = articleSaveService.fetchAndSave(null);
            log.info("[Stage 1~3] 수집/저장 완료 fetched={} saved={} duplicates={}",
                    result.getFetchedCount(), result.getSavedCount(), result.getDuplicateCount());
            return result;
        } catch (Exception e) {
            log.error("[Stage 1~3] 수집/저장 전체 실패: {}", e.getMessage(), e);
            // 빈 결과로 이후 단계 계속 진행
            return FetchAndSaveResult.builder()
                    .fetchedCount(0).savedCount(0).duplicateCount(0).failedCount(0)
                    .feeds(List.of()).executedAt(LocalDateTime.now()).build();
        }
    }

    private ClusteringSummary runClustering(int fromHours) {
        int totalClusters = 0, totalLinked = 0;
        List<String> failedCategories = new ArrayList<>();

        for (Category category : Category.values()) {
            try {
                ClusteringRequest req = new ClusteringRequest(category, fromHours);
                ClusteringResult result = topicClusteringService.cluster(req);
                totalClusters += result.getClustersCreated();
                totalLinked   += result.getLinkedArticleCount();
                log.info("[Stage 4] 클러스터링 완료 category={} clusters={} linked={}",
                        category, result.getClustersCreated(), result.getLinkedArticleCount());
            } catch (Exception e) {
                failedCategories.add(category.name());
                log.error("[Stage 4] 클러스터링 실패 category={} cause={}", category, e.getMessage(), e);
            }
        }
        return new ClusteringSummary(totalClusters, totalLinked, failedCategories);
    }

    private SummarySummary runSummarize() {
        try {
            List<SummarizeResponse> results = aiSummaryService.summarizeAll();
            log.info("[Stage 5] AI 요약 완료 generated={}", results.size());
            return new SummarySummary(results.size(), 0);
        } catch (Exception e) {
            log.error("[Stage 5] AI 요약 전체 실패: {}", e.getMessage(), e);
            return new SummarySummary(0, 1);
        }
    }

    private List<String> failedSources(FetchAndSaveResult result) {
        if (result.getFeeds() == null) return List.of();
        return result.getFeeds().stream()
                .filter(f -> !f.isCollectSuccess())
                .map(FetchAndSaveResult.FeedSummary::getFeedSourceCode)
                .toList();
    }

    // ── 내부 집계용 record ────────────────────────────────────────────────────

    private record ClusteringSummary(int clustersCreated, int linkedArticleCount, List<String> failedCategories) {}
    private record SummarySummary(int generated, int failed) {}
}
