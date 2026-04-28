package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.RssToJsonResult;
import com.crosschecknews.api.dto.TestPipelineResult;
import com.crosschecknews.api.service.TestPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test Pipeline", description = "DB 없이 로컬 XML 파일로 파이프라인 3단계를 검증하는 테스트 전용 API")
@RestController
@RequestMapping("/api/v1/test/pipeline")
@RequiredArgsConstructor
public class TestPipelineController {

    private final TestPipelineService testPipelineService;

    @Operation(
            summary = "[Test] RSS fixture XML 기반 전체 파이프라인 실행",
            description = """
                    rss.use-fixture=true 설정 시 네트워크 대신 로컬 fixture XML 파일에서 기사를 읽어
                    /api/v1/pipeline/collect 와 동일한 3단계 파이프라인을 실행합니다.

                    - Step 1 (fetch & save): rss-fixtures/*.xml 파싱 → 정규화 → DB 저장
                    - Step 2 (cluster): TF-IDF 기반 토픽 클러스터링 → Topic + TopicArticle DB 저장
                    - Step 3 (summarize): 요약이 없는 토픽에 AI 요약 생성

                    응답에는 각 토픽에 연결된 기사 목록과 TF-IDF 전처리 토큰이 포함되어
                    전처리 파이프라인(stemming, alias, 복합어 병합 등)을 직접 검증할 수 있습니다.

                    전제 조건:
                    - application.properties에 rss.use-fixture=true 설정
                    - src/main/resources/rss-fixtures/{FeedSourceCode}.xml 파일 존재
                    """
    )
    @PostMapping("/summarize")
    public TestPipelineResult summarize() {
        return testPipelineService.run();
    }

    @Operation(
            summary = "[Test] demo-data JSON → Article/Topic/Summary DB 로드",
            description = """
                    demo-data/ 디렉토리의 모든 JSON 파일(ArticleCandidate 구조)을 읽어
                    Article 저장 → TF-IDF 클러스터링 → AI 요약 전체 파이프라인을 실행합니다.
                    네트워크 없이 저장된 데이터만으로 H2를 시딩할 때 사용합니다.
                    """
    )
    @PostMapping("/demo-load")
    public TestPipelineResult loadFromDemoData() {
        return testPipelineService.loadFromDemoData();
    }

    @Operation(
            summary = "[Test] RSS 실시간 수집 → 날짜별 JSON 파일 저장",
            description = """
                    모든 FeedSource(FOX_WORLD, NYT_WORLD, CHOSUN_WORLD, HANI_WORLD)에서 RSS를 실시간으로 수집하고
                    {demo.data.path}/{yyyy-MM-dd}.json 경로에 저장합니다.

                    저장된 JSON은 ArticleCandidate 구조로, 이후 H2 DB 시드 데이터로 활용합니다.

                    주의: rss.use-fixture=false 설정 시 실제 네트워크를 통해 수집합니다.
                    """
    )
    @PostMapping("/rss-to-json")
    public RssToJsonResult rssToJson() {
        return testPipelineService.rssToJson();
    }

}
