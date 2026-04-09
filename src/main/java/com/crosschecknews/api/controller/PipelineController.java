package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.PipelineRequest;
import com.crosschecknews.api.dto.PipelineResult;
import com.crosschecknews.api.service.NewsIngestionPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pipeline", description = "RSS 수집 → 저장 → 클러스터링 → AI 요약 통합 파이프라인 API")
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final NewsIngestionPipelineService pipelineService;

    @Operation(
            summary = "전체 파이프라인 실행",
            description = "RSS 수집 → 정규화/저장 → Topic 클러스터링 → AI 요약을 순서대로 실행합니다. " +
                          "요청 바디 없이 호출하면 기본값(fromHours=48)이 적용됩니다. " +
                          "피드별/카테고리별 실패는 격리되며, 나머지 단계는 계속 진행됩니다.\n\n" +
                          "응답 구조:\n" +
                          "- fetchAndSave: 피드별 수집/저장 상세 결과 (FetchAndSaveResult)\n" +
                          "- clustering: 카테고리별 클러스터링 상세 결과 목록 (List<ClusteringResult>)\n" +
                          "- summaries: 생성된 AI 요약 목록 (List<SummarizeResponse>)\n" +
                          "실패한 카테고리는 clustering 목록에서 제외되고 서버 로그에만 기록됩니다."
    )
    @PostMapping("/collect")
    public PipelineResult collect(
            @Valid @RequestBody(required = false) PipelineRequest request
    ) {
        return pipelineService.run(request);
    }
}
