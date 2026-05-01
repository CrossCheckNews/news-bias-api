package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.dto.dashboard.PipelineStepHistoryResponse;
import com.crosschecknews.api.service.PipelineStepHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.crosschecknews.api.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Pipeline History", description = "파이프라인 단계 실행 이력 조회 API")
@RestController
@RequestMapping("/api/v1/pipeline/histories")
@RequiredArgsConstructor
public class PipelineStepHistoryController {

    private final PipelineStepHistoryService pipelineStepHistoryService;

    @Operation(
            summary = "파이프라인 단계 이력 조회",
            description = "PIPELINE_STEP_HISTORY 테이블 전체를 페이지네이션으로 조회합니다. " +
                          "startedAt 기준 최신순 정렬이며, date(yyyy-MM-dd)와 statuses(SUCCESS/PARTIAL_FAILED/FAILED/RUNNING) 파라미터로 필터링이 가능합니다. " +
                          "기존 status 단일 파라미터도 호환됩니다."
    )
    @GetMapping
    public PageResponse<PipelineStepHistoryResponse> getHistories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) PipelineStatus status,
            @RequestParam(required = false) List<PipelineStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(pipelineStepHistoryService.getHistories(date, mergeStatuses(status, statuses), page, size));
    }

    private List<PipelineStatus> mergeStatuses(PipelineStatus status, List<PipelineStatus> statuses) {
        List<PipelineStatus> merged = statuses == null ? new ArrayList<>() : new ArrayList<>(statuses);
        if (status != null && !merged.contains(status)) {
            merged.add(status);
        }
        return merged;
    }
}
