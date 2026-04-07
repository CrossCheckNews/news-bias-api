package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.FeedSource;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class FeedCollectResult {

    private String feedSourceCode;
    private String publisherName;
    private boolean success;
    private int count;
    private List<ArticleCandidate> articles;
    private String errorMessage;
    private LocalDateTime fetchedAt;

    public static FeedCollectResult success(FeedSource source, List<ArticleCandidate> articles) {
        return FeedCollectResult.builder()
                .feedSourceCode(source.name())
                .publisherName(source.getPublisherName())
                .success(true)
                .count(articles.size())
                .articles(articles)
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    public static FeedCollectResult failure(FeedSource source, String errorMessage) {
        return FeedCollectResult.builder()
                .feedSourceCode(source.name())
                .publisherName(source.getPublisherName())
                .success(false)
                .count(0)
                .articles(List.of())
                .errorMessage(errorMessage)
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
