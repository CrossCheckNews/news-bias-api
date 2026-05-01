package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.service.AiSummaryService;
import com.crosschecknews.api.service.TopicClusteringService;
import com.crosschecknews.api.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.crosschecknews.api.dto.PageResponse;
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

    @Operation(
            summary = "토픽 목록 조회",
            description = """
                    토픽 목록을 페이지 단위로 반환합니다.
                    - `status`: PENDING / ACTIVE / ARCHIVED 필터 (생략 시 전체)
                    - `date`: yyyy-MM-dd 형식의 생성일 필터 (생략 시 전체)
                    - `summary`: 언론사명 → 헤드라인 Map
                    - `leaningDistribution`: 정치 성향(CONSERVATIVE|PROGRESSIVE) → 기사 수 Map
                    - `countryDistribution`: 국가코드(KR|US|JP 등) → 기사 수 Map
                    """,
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "페이지 크기", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20"))
            }
    )
    @GetMapping
    public PageResponse<TopicResponse> findAll(
            @Parameter(description = "토픽 상태 필터 (PENDING / ACTIVE / ARCHIVED). 생략 시 전체 조회.")
            @RequestParam(required = false) TopicStatus status,
            @Parameter(description = "생성일 기준 날짜 필터 (yyyy-MM-dd). 예: 2026-04-06. 생략 시 전체 조회.")
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(topicService.findAll(status, date, page, size));
    }

    @Operation(
            summary = "토픽 단건 조회",
            description = """
                    ID로 토픽 상세 정보를 조회합니다.
                    - `articles`: 연결된 기사 목록 (url, publishedAt, description, 언론사 정보 포함)
                    - `leaningDistribution`: 정치 성향(CONSERVATIVE|PROGRESSIVE) → 기사 수 Map
                    - `countryDistribution`: 국가코드(KR|US 등) → 기사 수 Map
                    - `aiModel` / `summaryGeneratedAt`: AI 요약 생성 정보 (요약 전이면 null)
                    """,
            responses = {
                @ApiResponse(
                    responseCode = "200",
                    description = "토픽 상세 조회 성공",
                    content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                            name = "트럼프 관세 논란 토픽 예시",
                            value = """
                                {
                                  "id": 1,
                                  "title": "Trump announces reciprocal tariffs on all trading partners",
                                  "articleCategory": "WORLD",
                                  "status": "ACTIVE",
                                  "startDate": "2026-04-09",
                                  "createdAt": "2026-04-09T14:00:00",
                                  "aiSummary": "트럼프 대통령이 주요 교역국에 상호 관세를 부과하겠다고 발표하면서 국제 무역 갈등이 고조되고 있다.",
                                  "aiModel": "gemini-2.0-flash",
                                  "summaryGeneratedAt": "2026-04-09T15:30:00",
                                  "articleCount": 4,
                                  "articles": [
                                    {
                                      "articleId": 101,
                                      "headline": "Trump announces sweeping tariffs, vows to reshape global trade",
                                      "url": "https://www.foxnews.com/politics/trump-tariffs-global-trade-2026",
                                      "publishedAt": "2026-04-09T10:15:00",
                                      "description": "President Trump unveiled a broad tariff package targeting all major trading partners, calling it a historic move to restore American economic dominance.",
                                      "publisherName": "Fox News",
                                      "country": "US",
                                      "politicalLeaning": "CONSERVATIVE"
                                    },
                                    {
                                      "articleId": 102,
                                      "headline": "Trump's tariff blitz rattles markets and allies",
                                      "url": "https://www.nytimes.com/2026/04/09/world/trump-tariff-markets",
                                      "publishedAt": "2026-04-09T11:00:00",
                                      "description": "The sweeping tariff announcement sent stock markets tumbling and drew sharp criticism from European and Asian governments.",
                                      "publisherName": "New York Times",
                                      "country": "US",
                                      "politicalLeaning": "PROGRESSIVE"
                                    },
                                    {
                                      "articleId": 103,
                                      "headline": "트럼프 관세 폭탄, 한국 수출 기업에 직격탄",
                                      "url": "https://www.chosun.com/international/2026/04/09/trump-tariff-korea",
                                      "publishedAt": "2026-04-09T10:30:00",
                                      "description": "트럼프 대통령의 상호관세 발표로 반도체·자동차 등 대미 수출 의존도가 높은 한국 기업들의 피해가 우려된다.",
                                      "publisherName": "Chosun Ilbo",
                                      "country": "KR",
                                      "politicalLeaning": "CONSERVATIVE"
                                    },
                                    {
                                      "articleId": 104,
                                      "headline": "트럼프 관세, 글로벌 무역전쟁 재점화 우려",
                                      "url": "https://www.hani.co.kr/arti/international/2026/trump-tariff",
                                      "publishedAt": "2026-04-09T11:30:00",
                                      "description": "전문가들은 트럼프의 상호관세 조치가 세계 무역 질서를 흔들고 글로벌 경기 침체를 유발할 수 있다고 경고했다.",
                                      "publisherName": "Hankyoreh",
                                      "country": "KR",
                                      "politicalLeaning": "PROGRESSIVE"
                                    }
                                  ],
                                  "leaningDistribution": {
                                    "CONSERVATIVE": 2,
                                    "PROGRESSIVE": 2
                                  },
                                  "countryDistribution": {
                                    "US": 2,
                                    "KR": 2
                                  }
                                }
                                """
                        )
                    )
                ),
                @ApiResponse(responseCode = "404", description = "토픽을 찾을 수 없음")
            }
    )
    @GetMapping("/{id}")
    public TopicDetailResponse findById(@PathVariable Long id) {
        return topicService.findById(id);
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
