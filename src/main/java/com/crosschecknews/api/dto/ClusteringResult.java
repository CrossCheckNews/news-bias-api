package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ClusteringResult {

    private Category category;
    private int fromHours;
    private int scannedArticleCount;
    private int clustersCreated;
    private int linkedArticleCount;
    private List<TopicSummary> topics;
    private LocalDateTime executedAt;

    @Getter
    @Builder
    public static class TopicSummary {
        private Long topicId;
        private String title;
        private int articleCount;
        private int publisherCount;
        private List<String> publishers;
    }
}
