package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.dashboard.DashboardChartsResponse;
import com.crosschecknews.api.dto.dashboard.DashboardSummaryResponse;
import com.crosschecknews.api.service.DashboardService;
import com.crosschecknews.api.service.PipelineEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Dashboard", description = "데이터 파이프라인 모니터링 대시보드 API")
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PipelineEventPublisher eventPublisher;

    @Operation(summary = "파이프라인 이력/요약 대시보드")
    @GetMapping("/api/dashboard/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    @Operation(summary = "데이터 시각화 차트 대시보드")
    @GetMapping("/api/dashboard/charts")
    public DashboardChartsResponse getCharts() {
        return dashboardService.getCharts();
    }

    @Operation(summary = "SSE 기반 실시간 파이프라인 모니터링")
    @GetMapping(path = "/api/pipeline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipeline() {
        SseEmitter emitter = new SseEmitter(120_000L);
        eventPublisher.register(emitter);
        return emitter;
    }
}
