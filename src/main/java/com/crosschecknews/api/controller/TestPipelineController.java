package com.crosschecknews.api.controller;

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
}
