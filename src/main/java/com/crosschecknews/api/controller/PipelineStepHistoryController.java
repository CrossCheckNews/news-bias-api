package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.dashboard.PipelineStepHistoryResponse;
import com.crosschecknews.api.service.PipelineStepHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Pipeline History", description = "파이프라인 단계 실행 이력 조회 API")
@RestController
@RequestMapping("/api/v1/pipeline/histories")
@RequiredArgsConstructor
public class PipelineStepHistoryController {

    private final PipelineStepHistoryService pipelineStepHistoryService;

    @Operation(
            summary = "파이프라인 단계 이력 조회",
            description = "PIPELINE_STEP_HISTORY 테이블 전체를 페이지네이션으로 조회합니다. " +
                          "startedAt 기준 최신순 정렬이며, date 파라미터(yyyy-MM-dd)로 날짜 필터링이 가능합니다."
    )
    @GetMapping
    public Page<PipelineStepHistoryResponse> getHistories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pipelineStepHistoryService.getHistories(date, page, size);
    }
}
