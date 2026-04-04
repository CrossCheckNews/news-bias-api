package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Builder
public class TopicDetailResponse {

    private Long id;
    private String title;
    private String summary;
    private TopicStatus status;
    private LocalDate startDate;
    private long articleCount;
    private Map<String, Long> leaningDistribution;
    private Map<String, Long> countryDistribution;

    public static TopicDetailResponse from(
            Topic topic,
            long articleCount,
            Map<String, Long> leaningDistribution,
            Map<String, Long> countryDistribution
    ) {
        return TopicDetailResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .summary(topic.getSummary())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .articleCount(articleCount)
                .leaningDistribution(leaningDistribution)
                .countryDistribution(countryDistribution)
                .build();
    }
}
