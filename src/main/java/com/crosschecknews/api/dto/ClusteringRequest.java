package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.ArticleCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClusteringRequest {

    @NotNull
    @Schema(description = "클러스터링 대상 카테고리 (현재: WORLD)", example = "WORLD")
    private ArticleCategory category;

    @Min(1) @Max(168)
    @Schema(description = "수집 기준 시간 범위 (1~168시간, 기본값 48)", example = "48")
    private int fromHours = 48;
}
