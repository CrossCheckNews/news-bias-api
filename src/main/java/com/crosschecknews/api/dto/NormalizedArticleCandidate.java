package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.Publisher;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * ArticleCandidate → Article 저장 직전 형태.
 * HTML 제거, URL 정규화, 헤드라인 정규화, dedupeKey 생성, Publisher 참조 해소 완료.
 */
@Getter
@Builder
public class NormalizedArticleCandidate {

    private String headline;
    private String normalizedHeadline;  // 소문자, 공백 정리 — 중복 비교용
    private String url;
    private String normalizedUrl;       // 추적 파라미터 제거, trailing slash 정리
    private String description;         // HTML 제거됨
    private String rssGuid;
    private String dedupeKey;           // SHA-256(publisherName|normalizedHeadline|date)
    private LocalDateTime publishedAt;
    private Publisher publisher;
    private ArticleCategory category;
}
