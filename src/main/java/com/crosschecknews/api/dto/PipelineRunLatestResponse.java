package com.crosschecknews.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PipelineRunLatestResponse {
    private final String runDate;
    private final String today;
}
