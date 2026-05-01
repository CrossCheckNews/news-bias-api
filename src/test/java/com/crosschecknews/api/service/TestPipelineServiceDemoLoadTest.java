package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestPipelineServiceDemoLoadTest {

    @Mock ArticleSaveService            articleSaveService;
    @Mock TopicClusteringService        topicClusteringService;
    @Mock AiSummaryService              aiSummaryService;
    @Mock TopicArticleRepository        topicArticleRepository;
    @Mock RssCollectService             rssCollectService;
    @Mock PipelineRunRepository         pipelineRunRepository;
    @Mock PipelineStepHistoryRepository pipelineStepHistoryRepository;

    @InjectMocks TestPipelineService testPipelineService;

    @TempDir Path tempDir;

    private static final String ARTICLE_JSON = """
            [
              {
                "publisherCode": "FOX_WORLD",
                "publisherName": "Fox News",
                "country": "US",
                "politicalLeaning": "CONSERVATIVE",
                "category": "WORLD",
                "headline": "Test Headline Fox",
                "url": "https://fox.com/test",
                "description": "desc",
                "rssGuid": "guid-fox-1",
                "publishedAt": "2026-04-29T10:00:00"
              },
              {
                "publisherCode": "NYT_WORLD",
                "publisherName": "New York Times",
                "country": "US",
                "politicalLeaning": "PROGRESSIVE",
                "category": "WORLD",
                "headline": "Test Headline NYT",
                "url": "https://nyt.com/test",
                "description": "desc",
                "rssGuid": "guid-nyt-1",
                "publishedAt": "2026-04-29T10:00:00"
              }
            ]
            """;

    private static final String DEMO_DATA_WITH_FEED_ERRORS_JSON = """
            {
              "articles": [
                {
                  "publisherCode": "FOX_WORLD",
                  "publisherName": "Fox News",
                  "country": "US",
                  "politicalLeaning": "CONSERVATIVE",
                  "category": "WORLD",
                  "headline": "Test Headline Fox",
                  "url": "https://fox.com/test",
                  "description": "desc",
                  "rssGuid": "guid-fox-1",
                  "publishedAt": "2026-04-29T10:00:00"
                }
              ],
              "feedErrors": [
                {
                  "feedSourceCode": "NYT_WORLD",
                  "publisherName": "New York Times",
                  "errorType": "RSS_TIMEOUT",
                  "errorMessage": "Connection timeout after 30s"
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(testPipelineService, "demoDataPath", tempDir.toString());
        given(pipelineRunRepository.save(any())).willReturn(
                PipelineRun.builder()
                        .id(1L).status(PipelineStatus.RUNNING)
                        .fetchedCount(0).savedCount(0).clusteredCount(0).summarizedCount(0)
                        .startedAt(LocalDateTime.now())
                        .build());
    }

    private FetchAndSaveResult stubSaveResult(int saved) {
        return FetchAndSaveResult.builder()
                .fetchedCount(saved).savedCount(saved)
                .duplicateCount(0).failedCount(0)
                .feeds(List.of())
                .executedAt(LocalDateTime.now())
                .build();
    }

    private ClusteringResult emptyCluster() {
        return ClusteringResult.builder()
                .category(ArticleCategory.WORLD).fromHours(24)
                .scannedArticleCount(0).clustersCreated(0).linkedArticleCount(0)
                .topics(List.of()).executedAt(LocalDateTime.now())
                .build();
    }

    // ── 정상 케이스 ──────────────────────────────────────────────────────────

    @Test
    void JSON_파일이_있으면_기사를_파싱해서_저장_파이프라인을_실행한다() throws IOException {
        Files.writeString(tempDir.resolve("2026-04-29.json"), ARTICLE_JSON);

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(2));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        assertThat(result.getFetchAndSave().getSavedCount()).isEqualTo(2);
        verify(articleSaveService).fetchAndSaveFromResults(argThat(list ->
                list.size() == 2 &&
                list.stream().anyMatch(f -> f.getFeedSourceCode().equals("FOX_WORLD")) &&
                list.stream().anyMatch(f -> f.getFeedSourceCode().equals("NYT_WORLD"))
        ));
    }

    @Test
    void 여러_JSON_파일이_있으면_날짜별로_나눠서_처리한다() throws IOException {
        Files.writeString(tempDir.resolve("2026-04-28.json"), ARTICLE_JSON);
        Files.writeString(tempDir.resolve("2026-04-29.json"), ARTICLE_JSON);

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(2));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        assertThat(result.getFetchAndSave().getSavedCount()).isEqualTo(4);
        verify(articleSaveService, times(2)).fetchAndSaveFromResults(argThat(list ->
                list.stream().mapToInt(FeedCollectResult::getCount).sum() == 2
        ));
    }

    @Test
    void 실행_결과에_executedAt이_포함된다() throws IOException {
        Files.writeString(tempDir.resolve("2026-04-29.json"), ARTICLE_JSON);

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(2));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        assertThat(result.getExecutedAt()).isNotNull();
    }

    @Test
    void 객체형_demo_JSON의_feedErrors를_실패_피드로_전달한다() throws IOException {
        Files.writeString(tempDir.resolve("2026-04-29.json"), DEMO_DATA_WITH_FEED_ERRORS_JSON);

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(1));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        testPipelineService.loadFromDemoData();

        verify(articleSaveService).fetchAndSaveFromResults(argThat(list ->
                list.size() == 2 &&
                list.stream().anyMatch(f -> f.isSuccess() && f.getFeedSourceCode().equals("FOX_WORLD")) &&
                list.stream().anyMatch(f ->
                        !f.isSuccess() &&
                        f.getFeedSourceCode().equals("NYT_WORLD") &&
                        f.getErrorType().equals("RSS_TIMEOUT") &&
                        f.getErrorMessage().equals("Connection timeout after 30s"))
        ));
    }

    // ── 예외 케이스 ──────────────────────────────────────────────────────────

    @Test
    void demo_data_디렉토리가_없으면_빈_결과를_반환한다() {
        ReflectionTestUtils.setField(testPipelineService, "demoDataPath", "/nonexistent/path");

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(0));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        assertThat(result.getFetchAndSave().getFetchedCount()).isEqualTo(0);
        verify(articleSaveService).fetchAndSaveFromResults(argThat(List::isEmpty));
    }

    @Test
    void JSON_파일이_없으면_빈_결과를_반환한다() {
        // tempDir은 존재하지만 파일 없음
        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(0));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        assertThat(result.getFetchAndSave().getSavedCount()).isEqualTo(0);
        verify(articleSaveService).fetchAndSaveFromResults(argThat(List::isEmpty));
    }

    @Test
    void 깨진_JSON_파일은_건너뛰고_정상_파일만_처리한다() throws IOException {
        Files.writeString(tempDir.resolve("broken.json"), "{ invalid json }}}");
        Files.writeString(tempDir.resolve("2026-04-29.json"), ARTICLE_JSON);

        given(articleSaveService.fetchAndSaveFromResults(any())).willReturn(stubSaveResult(2));
        given(topicClusteringService.cluster(any())).willReturn(emptyCluster());
        given(aiSummaryService.summarizeAll()).willReturn(List.of());

        TestPipelineResult result = testPipelineService.loadFromDemoData();

        // 정상 파일 2건만 처리
        assertThat(result.getFetchAndSave().getSavedCount()).isEqualTo(2);
    }
}
