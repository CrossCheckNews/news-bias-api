package com.crosschecknews.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "article", uniqueConstraints = {
        @UniqueConstraint(name = "uq_article_normalized_url", columnNames = "normalized_url"),
        @UniqueConstraint(name = "uq_article_dedupe_key",    columnNames = "dedupe_key")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String headline;

    // 비교용 정규화 헤드라인 (소문자, 공백 정리)
    @Column(nullable = false)
    private String normalizedHeadline;

    @Column(nullable = false)
    private String url;

    // 추적 파라미터 제거 + trailing slash 정리 + 소문자화
    @Column(name = "normalized_url", nullable = false, unique = true)
    private String normalizedUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleCategory category;

    // RSS <guid> 또는 Atom <id> — 출처별 식별자 (publisher 내 고유)
    private String rssGuid;

    // SHA-256(publisherName + "|" + normalizedHeadline + "|" + publishedDate)
    // rssGuid/normalizedUrl 모두 없거나 변경됐을 때 최후 중복 판별 키
    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onFetch() {
        this.fetchedAt = LocalDateTime.now();
    }
}
