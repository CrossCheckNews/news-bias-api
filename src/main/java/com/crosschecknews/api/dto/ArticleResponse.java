package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.Category;
import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArticleResponse {

    private Long id;
    private String headline;
    private String url;
    private String normalizedUrl;
    private String description;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private Long publisherId;
    private String publisherName;
    private Country publisherCountry;
    private PoliticalLeaning publisherLeaning;
    private Category category;

    public static ArticleResponse from(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .headline(article.getHeadline())
                .url(article.getUrl())
                .normalizedUrl(article.getNormalizedUrl())
                .description(article.getDescription())
                .publishedAt(article.getPublishedAt())
                .fetchedAt(article.getFetchedAt())
                .publisherId(article.getPublisher().getId())
                .publisherName(article.getPublisher().getName())
                .publisherCountry(article.getPublisher().getCountry())
                .publisherLeaning(article.getPublisher().getPoliticalLeaning())
                .category(article.getCategory())
                .build();
    }
}
