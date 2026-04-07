package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Category;
import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TopicResponse {

    private Long id;
    private String title;
    private String summary;
    private String aiSummary;
    private Category category;
    private TopicStatus status;
    private LocalDate startDate;
    private LocalDateTime createdAt;
    private long articleCount;

    public static TopicResponse from(Topic topic, long articleCount) {
        return TopicResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .summary(topic.getSummary())
                .aiSummary(topic.getAiSummary())
                .category(topic.getCategory())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .createdAt(topic.getCreatedAt())
                .articleCount(articleCount)
                .build();
    }
}
