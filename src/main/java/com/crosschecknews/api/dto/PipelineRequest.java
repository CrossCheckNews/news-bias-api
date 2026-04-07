package com.crosschecknews.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PipelineRequest {

    /** 클러스터링 대상 기사 범위 (시간 기준, 기본값 48시간) */
    @Min(1) @Max(168)
    private int fromHours = 48;
}
