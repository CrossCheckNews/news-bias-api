package com.crosschecknews.api.dto;

import com.crosschecknews.api.service.ArticleDeduplicationService.DuplicateReason;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FetchAndSaveResult {

    private int fetchedCount;
    private int savedCount;
    private int duplicateCount;
    private int failedCount;
    private Map<String, Integer> duplicateReasonCounts; // RSS_GUID, NORMALIZED_URL, DEDUPE_KEY별 집계
    private List<FeedSummary> feeds;
    private LocalDateTime executedAt;

    @Getter
    @Builder
    public static class FeedSummary {
        private String feedSourceCode;
        private String publisherName;
        private boolean collectSuccess;
        private int fetched;
        private int saved;
        private int duplicates;
        private int failed;
        private String errorMessage;
    }
}
