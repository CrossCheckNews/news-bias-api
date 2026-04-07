package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class TopicComparisonResponse {

    private Long topicId;
    private String topicTitle;
    private String briefSummary;   // Topic.aiSummary — AI가 생성한 브리핑
    private String category;
    private String groupBy;        // "leaning" | "country" | null (flat)

    /** 정렬된 기사 목록 (groupBy 여부와 무관하게 항상 포함) */
    private List<ArticleItem> articles;

    /** groupBy 지정 시 추가로 포함되는 그룹 구조 (null = flat 모드) */
    private List<Group> groups;

    // ── 내부 DTO ────────────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class ArticleItem {
        private Long articleId;
        private String publisher;
        private String country;
        private String politicalLeaning;
        private String headline;
        private String url;
        private LocalDateTime publishedAt;
    }

    @Getter
    @Builder
    public static class Group {
        private String key;
        private List<ArticleItem> articles;
    }

    // ── 팩토리 ────────────────────────────────────────────────────────────────

    /**
     * TopicArticle 목록과 groupBy 파라미터로 비교 응답을 조립한다.
     *
     * <p>기본 정렬: country(가나다/알파벳) → politicalLeaning(enum 선언 순) → publishedAt(최신순)
     */
    public static TopicComparisonResponse of(
            Topic topic,
            List<TopicArticle> links,
            String groupBy
    ) {
        List<ArticleItem> sorted = links.stream()
                .map(TopicComparisonResponse::toItem)
                .sorted(defaultOrder())
                .toList();

        List<Group> groups = buildGroups(sorted, groupBy);

        return TopicComparisonResponse.builder()
                .topicId(topic.getId())
                .topicTitle(topic.getTitle())
                .briefSummary(topic.getAiSummary())
                .category(topic.getCategory().name())
                .groupBy(groups != null ? groupBy.toLowerCase() : null)
                .articles(sorted)
                .groups(groups)
                .build();
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private static ArticleItem toItem(TopicArticle ta) {
        var article   = ta.getArticle();
        var publisher = article.getPublisher();
        return ArticleItem.builder()
                .articleId(article.getId())
                .publisher(publisher.getName())
                .country(publisher.getCountry().name())
                .politicalLeaning(publisher.getPoliticalLeaning().name())
                .headline(article.getHeadline())
                .url(article.getUrl())
                .publishedAt(article.getPublishedAt())
                .build();
    }

    private static Comparator<ArticleItem> defaultOrder() {
        return Comparator
                .comparing(ArticleItem::getCountry)
                .thenComparing(ArticleItem::getPoliticalLeaning)
                .thenComparing(Comparator.comparing(
                        ArticleItem::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ));
    }

    private static List<Group> buildGroups(List<ArticleItem> sorted, String groupBy) {
        if (groupBy == null || groupBy.isBlank()) return null;

        java.util.function.Function<ArticleItem, String> keyFn =
                "country".equalsIgnoreCase(groupBy)
                        ? ArticleItem::getCountry
                        : ArticleItem::getPoliticalLeaning;   // default: leaning

        // LinkedHashMap으로 삽입 순서(이미 정렬된 순서) 유지
        Map<String, List<ArticleItem>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(keyFn,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(e -> Group.builder()
                        .key(e.getKey())
                        .articles(e.getValue())
                        .build())
                .toList();
    }
}
