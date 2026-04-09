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
            summary = "[Test] XML 파일 기반 파이프라인 전체 실행",
            description = """
                    DB 없이 src/main/resources/test-data/test-articles.xml을 읽어
                    파이프라인 3단계를 순서대로 실행합니다.

                    - Step 1 (fetch): XML에서 기사 4건 파싱 (영어 보수/진보, 한국어 보수/진보)
                    - Step 2 (cluster): 전체를 하나의 토픽으로 그룹화
                    - Step 3 (summarize): Gemini API로 AI 요약 생성

                    GEMINI_API_KEY 환경변수가 설정되어 있어야 합니다.
                    """
    )
    @PostMapping("/summarize")
    public TestPipelineResult summarize() {
        return testPipelineService.run();
    }
}
