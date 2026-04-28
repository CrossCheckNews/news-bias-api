package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RssToJsonResult {

    private LocalDate date;
    private String savedFilePath;
    private int totalArticles;
    private List<FeedSummary> feeds;
    private LocalDateTime executedAt;

    @Getter
    @Builder
    public static class FeedSummary {
        private String feedSourceCode;
        private String publisherName;
        private boolean success;
        private int count;
        private String errorMessage;
    }
}
