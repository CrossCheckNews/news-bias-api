package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.dto.ClusteringRequest;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.dto.FetchAndSaveResult;
import com.crosschecknews.api.dto.SummarizeResponse;
import com.crosschecknews.api.service.AiSummaryService;
import com.crosschecknews.api.service.ArticleSaveService;
import com.crosschecknews.api.service.TopicClusteringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pipeline Steps", description = "파이프라인 단계별 개별 실행 API (검증/디버깅용)")
@RestController
@RequestMapping("/api/v1/pipeline/steps")
@RequiredArgsConstructor
@Validated
public class PipelineStepController {

    private final ArticleSaveService    articleSaveService;
    private final TopicClusteringService topicClusteringService;
    private final AiSummaryService      aiSummaryService;

    /**
     * Stage 1~3: RSS 수집 → 정규화 → 저장
     */
    @Operation(
            summary = "[Step 1] RSS 수집 + 정규화 + 저장",
            description = "모든 FeedSource에서 RSS를 수집하고, 정규화 후 중복 제거하여 저장합니다. " +
                          "피드별 수집/저장 결과를 상세히 반환합니다."
    )
    @PostMapping("/fetch-save")
    public FetchAndSaveResult fetchAndSave() {
        return articleSaveService.fetchAndSaveAll();
    }

    /**
     * Stage 4: 클러스터링
     */
    @Operation(
            summary = "[Step 2] 토픽 클러스터링",
            description = "저장된 아티클을 기반으로 토픽을 생성하고 아티클을 연결합니다. " +
                          "fromHours 범위 내 아티클만 대상으로 합니다 (기본값 48시간). " +
                          "현재 지원 카테고리: WORLD"
    )
    @PostMapping("/cluster")
    public ClusteringResult cluster(
            @RequestParam(defaultValue = "WORLD") ArticleCategory articleCategory,
            @RequestParam(defaultValue = "48") @Min(1) @Max(168) int fromHours
    ) {
        return topicClusteringService.cluster(new ClusteringRequest(articleCategory, fromHours));
    }

    /**
     * Stage 5: AI 요약
     */
    @Operation(
            summary = "[Step 3] AI 요약 생성",
            description = "요약이 없는 토픽에 대해 AI 요약을 생성합니다. " +
                          "생성된 요약 목록을 반환합니다."
    )
    @PostMapping("/summarize")
    public List<SummarizeResponse> summarize() {
        return aiSummaryService.summarizeAll();
    }
}
