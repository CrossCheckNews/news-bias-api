package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.service.AiSummaryService;
import com.crosschecknews.api.service.TopicClusteringService;
import com.crosschecknews.api.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Topic", description = "토픽(이슈) 관리 및 언론사 관점 비교 API")
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final TopicClusteringService topicClusteringService;
    private final AiSummaryService aiSummaryService;

    @Operation(summary = "토픽 생성", description = "동일한 이슈를 다루는 기사들을 묶는 토픽을 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@Valid @RequestBody TopicRequest request) {
        return topicService.create(request);
    }

    @Operation(
            summary = "토픽 목록 조회",
            description = "토픽 목록을 반환합니다. status, date 파라미터로 필터링할 수 있습니다.",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "페이지 크기", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20")),
                    @Parameter(name = "sort", description = "정렬 (예: createdAt,desc)", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "createdAt,desc"))
            }
    )
    @GetMapping
    public Page<TopicResponse> findAll(
            @Parameter(description = "토픽 상태 필터 (PENDING / ACTIVE / ARCHIVED). 생략 시 전체 조회.")
            @RequestParam(required = false) TopicStatus status,
            @Parameter(description = "생성일 기준 날짜 필터 (yyyy-MM-dd). 예: 2026-04-06. 생략 시 전체 조회.")
            @RequestParam(required = false) LocalDate date,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return topicService.findAll(status, date, pageable);
    }

    @Operation(summary = "토픽 단건 조회", description = "ID로 토픽 상세 정보와 연결된 기사 목록을 조회합니다.")
    @GetMapping("/{id}")
    public TopicDetailResponse findById(@PathVariable Long id) {
        return topicService.findById(id);
    }

    @Operation(summary = "토픽 수정", description = "토픽 제목, 설명, 상태를 수정합니다.")
    @PutMapping("/{id}")
    public TopicResponse update(@PathVariable Long id, @Valid @RequestBody TopicRequest request) {
        return topicService.update(id, request);
    }

    @Operation(summary = "토픽 삭제", description = "토픽을 삭제합니다. 연결된 기사는 삭제되지 않습니다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        topicService.delete(id);
    }

    @Operation(summary = "언론사 관점 비교 조회",
            description = "토픽에 연결된 기사 목록을 AI 브리핑과 함께 반환합니다. " +
                          "groupBy=leaning 또는 groupBy=country 지정 시 groups 필드가 추가됩니다. " +
                          "미지정 시 country → politicalLeaning → publishedAt(최신순) 기준으로 정렬된 flat 목록을 반환합니다.")
    @GetMapping("/{id}/articles")
    public TopicComparisonResponse getComparisonView(
            @PathVariable Long id,
            @RequestParam(required = false) String groupBy
    ) {
        return topicService.getComparisonView(id, groupBy);
    }

    @Operation(summary = "언론사 관점 비교 조회 (별칭)",
            description = "/articles와 동일한 응답을 반환합니다.")
    @GetMapping("/{id}/comparison")
    public TopicComparisonResponse getComparisonViewAlias(
            @PathVariable Long id,
            @RequestParam(required = false) String groupBy
    ) {
        return topicService.getComparisonView(id, groupBy);
    }

    @Operation(summary = "토픽에 기사 연결", description = "기존 기사를 토픽에 연결합니다.")
    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void linkArticle(@PathVariable Long id, @Valid @RequestBody TopicArticleLinkRequest request) {
        topicService.linkArticle(id, request.getArticleId());
    }

    @Operation(summary = "토픽-기사 연결 해제", description = "토픽에서 특정 기사의 연결을 제거합니다.")
    @DeleteMapping("/{id}/articles/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkArticle(@PathVariable Long id, @PathVariable Long articleId) {
        topicService.unlinkArticle(id, articleId);
    }

    @Operation(summary = "토픽 단건 AI 요약", description = "토픽에 연결된 기사 헤드라인을 기반으로 AI 브리핑을 생성합니다.")
    @PostMapping("/{id}/summarize")
    public SummarizeResponse summarize(@PathVariable Long id) {
        return aiSummaryService.summarize(id);
    }

    @Operation(summary = "미요약 토픽 일괄 AI 요약",
            description = "aiSummary가 없는 ACTIVE 토픽 전체를 일괄 요약합니다. 개별 실패는 건너뜁니다.")
    @PostMapping("/summarize")
    public List<SummarizeResponse> summarizeAll() {
        return aiSummaryService.summarizeAll();
    }

    @Operation(
            summary = "기사 자동 클러스터링",
            description = "최근 기사들을 제목 유사도 기반으로 클러스터링해 Topic을 자동 생성합니다. " +
                          "2개 이상의 다른 언론사 기사가 묶인 클러스터만 Topic으로 생성됩니다. " +
                          "이미 ACTIVE Topic에 연결된 기사는 제외됩니다."
    )
    @PostMapping("/cluster")
    public ClusteringResult cluster(@Valid @RequestBody ClusteringRequest request) {
        return topicClusteringService.cluster(request);
    }
}
