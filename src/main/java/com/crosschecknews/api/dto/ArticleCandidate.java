package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.FeedSource;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.service.RssFetchService;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArticleCandidate {

    private String publisherCode;       // e.g. "FOX_WORLD"
    private String publisherName;       // e.g. "Fox News"
    private Country country;
    private PoliticalLeaning politicalLeaning;
    private ArticleCategory category;
    private String headline;
    private String url;
    private String description;
    private String rssGuid;
    private LocalDateTime publishedAt;

    public static ArticleCandidate of(FeedSource source, RssFetchService.RssEntry entry) {
        return ArticleCandidate.builder()
                .publisherCode(source.name())
                .publisherName(source.getPublisherName())
                .country(source.getCountry())
                .politicalLeaning(source.getPoliticalLeaning())
                .category(source.getArticleCategory())
                .headline(entry.title())
                .url(entry.link())
                .description(entry.description())
                .rssGuid(entry.guid())
                .publishedAt(entry.publishedAt())
                .build();
    }
}
