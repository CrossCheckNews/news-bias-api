package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TestPipelineResult {

    // Step 1: 파싱 결과
    private List<ArticleInfo> articles;

    // Step 2: 클러스터링 결과 (테스트이므로 전체를 하나의 토픽으로 묶음)
    private String topicTitle;

    // Step 3: AI 요약 결과
    private String aiSummary;
    private String aiModel;
    private LocalDateTime generatedAt;

    @Getter
    @Builder
    public static class ArticleInfo {
        private String publisher;
        private String country;
        private String politicalLeaning;
        private String headline;
        private String description;
    }
}
