package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Topic;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SummarizeResponse {

    private Long topicId;
    private String title;
    private String summary;
    private String aiModel;
    private LocalDateTime generatedAt;

    public static SummarizeResponse from(Topic topic) {
        return SummarizeResponse.builder()
                .topicId(topic.getId())
                .title(topic.getTitle())
                .summary(topic.getAiSummary())
                .aiModel(topic.getAiModel())
                .generatedAt(topic.getSummaryGeneratedAt())
                .build();
    }
}
