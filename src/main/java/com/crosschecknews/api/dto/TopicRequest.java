package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.TopicStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TopicRequest {

    @NotBlank
    @Schema(description = "토픽 제목", example = "미국 관세 정책 논란")
    private String title;

    @Schema(description = "토픽 수동 요약 (선택)", example = "미국의 추가 관세 부과 방침을 둘러싼 각국 반응")
    private String summary;

    @NotNull
    @Schema(description = "카테고리 (현재: WORLD)", example = "WORLD")
    private ArticleCategory articleCategory;

    @NotNull
    @Schema(description = "토픽 상태 (PENDING / ACTIVE / ARCHIVED)", example = "ACTIVE")
    private TopicStatus status;

    @Schema(description = "이슈 시작일 (yyyy-MM-dd)", example = "2026-04-01")
    private LocalDate startDate;
}
