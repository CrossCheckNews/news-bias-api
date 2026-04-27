package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.domain.TopicStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "토픽 상세 응답")
public class TopicDetailResponse {

    // ── Topic ────────────────────────────────────────────────────────────────

    @Schema(description = "토픽 ID", example = "1")
    private Long id;

    @Schema(
        description = "토픽 제목 (클러스터 내 대표 헤드라인)",
        example = "Trump announces reciprocal tariffs on all trading partners"
    )
    private String title;

    @Schema(description = "카테고리", example = "WORLD")
    private ArticleCategory articleCategory;

    @Schema(description = "토픽 상태", allowableValues = {"PENDING", "ACTIVE", "ARCHIVED"}, example = "ACTIVE")
    private TopicStatus status;

    @Schema(description = "최초 기사 발행일 (yyyy-MM-dd)", example = "2026-04-09")
    private LocalDate startDate;

    @Schema(description = "토픽 생성 일시", example = "2026-04-09T14:00:00")
    private LocalDateTime createdAt;

    // ── AI 요약 ───────────────────────────────────────────────────────────────

    @Schema(
        description = "AI 브리핑 요약문. 요약 생성 전이면 null.",
        example = "트럼프 대통령이 주요 교역국에 상호 관세를 부과하겠다고 발표하면서 국제 무역 갈등이 고조되고 있다."
    )
    private String aiSummary;

    @Schema(description = "요약 생성에 사용된 AI 모델. 요약 생성 전이면 null.", example = "gemini-2.0-flash")
    private String aiModel;

    @Schema(description = "요약 생성 일시. 요약 생성 전이면 null.", example = "2026-04-09T15:30:00")
    private LocalDateTime summaryGeneratedAt;

    // ── 기사 목록 ──────────────────────────────────────────────────────────────

    @Schema(description = "연결된 기사 수", example = "4")
    private long articleCount;

    @Schema(description = "연결된 기사 목록 (publisher 정보 포함)")
    private List<ArticleEntry> articles;

    // ── 분포 집계 ──────────────────────────────────────────────────────────────

    @Schema(
        description = "정치 성향별 기사 수. key=성향(CONSERVATIVE|PROGRESSIVE), value=기사 수",
        example = "{\"CONSERVATIVE\": 2, \"PROGRESSIVE\": 2}"
    )
    private Map<String, Integer> leaningDistribution;

    @Schema(
        description = "국가별 기사 수. key=국가코드(KR|US 등), value=기사 수",
        example = "{\"US\": 2, \"KR\": 2}"
    )
    private Map<String, Integer> countryDistribution;

    // ── 내부 DTO ───────────────────────────────────────────────────────────────

    @Getter
    @Builder
    @Schema(description = "토픽에 연결된 기사 (publisher 정보 포함)")
    public static class ArticleEntry {

        @Schema(description = "기사 ID", example = "103")
        private Long articleId;

        @Schema(
            description = "기사 헤드라인",
            example = "트럼프 관세 폭탄, 한국 수출 기업에 직격탄"
        )
        private String headline;

        @Schema(
            description = "기사 원문 URL",
            example = "https://www.chosun.com/international/2026/04/09/trump-tariff-korea"
        )
        private String url;

        @Schema(description = "기사 발행 일시. RSS에 날짜가 없으면 null.", example = "2026-04-09T10:30:00")
        private LocalDateTime publishedAt;

        @Schema(
            description = "RSS description 필드. 없으면 null.",
            example = "트럼프 대통령의 상호관세 발표로 반도체·자동차 등 대미 수출 의존도가 높은 한국 기업들의 피해가 우려된다."
        )
        private String description;

        @Schema(description = "언론사명", example = "Chosun Ilbo")
        private String publisherName;

        @Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "KR")
        private String country;

        @Schema(description = "정치 성향 (CONSERVATIVE | PROGRESSIVE)", example = "CONSERVATIVE")
        private String politicalLeaning;
    }

    // ── 팩토리 ────────────────────────────────────────────────────────────────

    public static TopicDetailResponse from(Topic topic, List<TopicArticle> links) {
        List<ArticleEntry> articles = links.stream()
                .map(ta -> ArticleEntry.builder()
                        .articleId(ta.getArticle().getId())
                        .headline(ta.getArticle().getHeadline())
                        .url(ta.getArticle().getUrl())
                        .publishedAt(ta.getArticle().getPublishedAt())
                        .description(ta.getArticle().getDescription())
                        .publisherName(ta.getArticle().getPublisher().getName())
                        .country(ta.getArticle().getPublisher().getCountry().name())
                        .politicalLeaning(ta.getArticle().getPublisher().getPoliticalLeaning().name())
                        .build())
                .toList();

        Map<String, Integer> leaningDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getPoliticalLeaning().name(),
                        Collectors.summingInt(x -> 1)
                ));

        Map<String, Integer> countryDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getCountry().name(),
                        Collectors.summingInt(x -> 1)
                ));

        return TopicDetailResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .articleCategory(topic.getArticleCategory())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .createdAt(topic.getCreatedAt())
                .aiSummary(topic.getAiSummary())
                .aiModel(topic.getAiModel())
                .summaryGeneratedAt(topic.getSummaryGeneratedAt())
                .articleCount(links.size())
                .articles(articles)
                .leaningDistribution(leaningDistribution)
                .countryDistribution(countryDistribution)
                .build();
    }
}
