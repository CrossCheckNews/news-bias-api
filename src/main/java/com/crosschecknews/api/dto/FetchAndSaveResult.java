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
    private List<ArticleValidationError> validationErrors;
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
        private String errorType;
        private String errorMessage;
    }

    @Getter
    @Builder
    public static class ArticleValidationError {
        private String feedSourceCode;
        private String targetName;   // url 또는 rssGuid 또는 "UNKNOWN"
        private String errorType;    // e.g. "MISSING_REQUIRED_FIELD"
        private String errorMessage; // 누락된 필드 설명
    }
}
