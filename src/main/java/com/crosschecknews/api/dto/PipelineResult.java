package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PipelineResult {

    private Long pipelineRunId;

    // ── Stage 1~3: RSS 수집 / 정규화 / 저장 ──────────────────────────────────
    private FetchAndSaveResult fetchAndSave;

    // ── Stage 4: Topic 클러스터링 (카테고리별) ───────────────────────────────
    private List<ClusteringResult> clustering;

    // ── Stage 5: AI 요약 ─────────────────────────────────────────────────────
    private List<SummarizeResponse> summaries;

    private LocalDateTime executedAt;
}
