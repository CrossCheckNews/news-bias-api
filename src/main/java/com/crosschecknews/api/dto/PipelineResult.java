package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PipelineResult {

    // ── Stage 1~3: RSS 수집 / 정규화 / 저장 ──────────────────────────────────
    private int fetchedCount;
    private int savedCount;
    private int duplicateCount;

    // ── Stage 4: Topic 클러스터링 ────────────────────────────────────────────
    private int clustersCreated;
    private int linkedArticleCount;

    // ── Stage 5: AI 요약 ─────────────────────────────────────────────────────
    private int summariesGenerated;

    // ── 실패 정보 ────────────────────────────────────────────────────────────
    /** RSS 수집 실패 피드 코드 목록 */
    private List<String> failedSources;

    /** 클러스터링 실패 카테고리 목록 */
    private List<String> failedCategories;

    /** AI 요약 실패 토픽 수 */
    private int summaryFailedCount;

    private LocalDateTime executedAt;
}
