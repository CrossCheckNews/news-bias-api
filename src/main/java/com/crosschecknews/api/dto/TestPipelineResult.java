package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TestPipelineResult {

    /** Step 1: RSS fixture 수집 + DB 저장 결과 */
    private FetchAndSaveResult fetchAndSave;

    /** Step 2: 클러스터링으로 생성된 토픽 목록 (각 토픽에 연결된 기사 포함) */
    private List<TopicResult> topics;

    /** Step 3: AI 요약 결과 */
    private List<SummarizeResponse> summaries;

    private LocalDateTime executedAt;

    @Getter
    @Builder
    public static class TopicResult {
        private Long topicId;
        private String title;
        private List<ArticleInfo> articles;
    }

    @Getter
    @Builder
    public static class ArticleInfo {
        private String publisher;
        private String country;
        private String politicalLeaning;
        private String headline;
        private String normalizedHeadline;
        /** TfIdfVectorizer.tokenize() 결과 — 전처리 파이프라인 검증용 */
        private List<String> tokens;
        private String description;
    }
}
