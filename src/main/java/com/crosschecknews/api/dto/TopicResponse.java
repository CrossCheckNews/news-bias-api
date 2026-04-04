package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class TopicResponse {

    private Long id;
    private String title;
    private String summary;
    private TopicStatus status;
    private LocalDate startDate;
    private long articleCount;

    public static TopicResponse from(Topic topic, long articleCount) {
        return TopicResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .summary(topic.getSummary())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .articleCount(articleCount)
                .build();
    }
}
