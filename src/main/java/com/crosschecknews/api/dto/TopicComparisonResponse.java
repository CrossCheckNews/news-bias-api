package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TopicComparisonResponse {

    private Long topicId;
    private String groupBy;
    private List<Group> groups;

    @Getter
    @Builder
    public static class Group {
        private String key;
        private List<ArticleResponse> articles;
    }
}
