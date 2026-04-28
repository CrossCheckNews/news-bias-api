package com.crosschecknews.api.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardChartsResponse {
    private List<NamedCount> articlesByPublisher;
    private List<NamedCount> topicsByCountry;
    private List<NamedCount> pipelineStatusCounts;

    @Getter
    @Builder
    public static class NamedCount {
        private String name;
        private long count;
    }
}
