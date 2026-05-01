package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.FeedSource;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.domain.PipelineStepHistory;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.util.TfIdfVectorizer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RSS fixture XML → DB 저장 → 클러스터링 → AI 요약 전체 파이프라인을 실행한다.
 *
 * <p>/api/v1/pipeline/collect 와 동일한 흐름이되, rss.use-fixture=true 설정 시
 * 네트워크 대신 src/main/resources/rss-fixtures/*.xml 파일에서 기사를 읽는다.
 * 각 기사의 TF-IDF 토큰을 응답에 포함해 전처리 파이프라인을 함께 검증할 수 있다.
 *
 * <p>Step 1: RSS fixture → 정규화 → DB 저장 (ArticleSaveService)
 * <p>Step 2: TF-IDF 클러스터링 → Topic + TopicArticle DB 저장 (TopicClusteringService)
 * <p>Step 3: AI 요약 생성 (AiSummaryService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPipelineService {

    private final ArticleSaveService            articleSaveService;
    private final TopicClusteringService        topicClusteringService;
    private final AiSummaryService              aiSummaryService;
    private final TopicArticleRepository        topicArticleRepository;
    private final RssCollectService             rssCollectService;
    private final PipelineRunRepository         pipelineRunRepository;
    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;

    @Value("${demo.data.path:demo-data}")
    private String demoDataPath;

    public TestPipelineResult run() {
        // Step 1: fixture XML 읽기 → 정규화 → DB 저장
        FetchAndSaveResult fetchAndSave = articleSaveService.fetchAndSaveAll();
        log.info("[Test Step 1] 수집/저장 완료 fetched={} saved={} duplicates={}",
                fetchAndSave.getFetchedCount(), fetchAndSave.getSavedCount(), fetchAndSave.getDuplicateCount());

        // Step 2: TF-IDF 클러스터링 + DB 저장, 생성된 토픽별 기사 조회
        List<TestPipelineResult.TopicResult> topics = buildTopicResults();
        log.info("[Test Step 2] 클러스터링 완료 topics={}", topics.size());

        // Step 3: AI 요약
        List<SummarizeResponse> summaries = aiSummaryService.summarizeAll();
        log.info("[Test Step 3] AI 요약 완료 generated={}", summaries.size());

        return TestPipelineResult.builder()
                .fetchAndSave(fetchAndSave)
                .topics(topics)
                .summaries(summaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 모든 FeedSource RSS를 실시간 수집해 날짜별 JSON 파일로 저장한다.
     * 저장 경로: {demo.data.path}/{yyyy-MM-dd}.json
     */
    public RssToJsonResult rssToJson() {
        LocalDate today = LocalDate.now();
        List<FeedCollectResult> feedResults = rssCollectService.collectAll();

        List<ArticleCandidate> allArticles = new ArrayList<>();
        List<RssToJsonResult.FeedSummary> feedSummaries = new ArrayList<>();

        for (FeedCollectResult result : feedResults) {
            if (result.isSuccess()) {
                allArticles.addAll(result.getArticles());
                feedSummaries.add(RssToJsonResult.FeedSummary.builder()
                        .feedSourceCode(result.getFeedSourceCode())
                        .publisherName(result.getPublisherName())
                        .success(true)
                        .count(result.getCount())
                        .build());
            } else {
                feedSummaries.add(RssToJsonResult.FeedSummary.builder()
                        .feedSourceCode(result.getFeedSourceCode())
                        .publisherName(result.getPublisherName())
                        .success(false)
                        .count(0)
                        .errorMessage(result.getErrorMessage())
                        .build());
            }
        }

        String filePath = saveToJson(allArticles, today);
        log.info("[rssToJson] 저장 완료 date={} articles={} path={}", today, allArticles.size(), filePath);

        return RssToJsonResult.builder()
                .date(today)
                .savedFilePath(filePath)
                .totalArticles(allArticles.size())
                .feeds(feedSummaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private String saveToJson(List<ArticleCandidate> articles, LocalDate date) {
        try {
            File dir = new File(demoDataPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, date + ".json");

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, articles);

            return file.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("JSON 파일 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * demo-data/ 디렉토리의 JSON 파일을 날짜별 파이프라인 실행으로 나누어 처리한다.
     * 기사의 publishedAt은 JSON 원본 값을 유지하고, fetchedAt/실행 이력 시간은 실제 실행 시각으로 기록된다.
     */
    public TestPipelineResult loadFromDemoData() {
        List<DemoDataBatch> batches = readDemoJsonBatches();
        if (batches.isEmpty()) {
            batches = List.of(new DemoDataBatch(null, "EMPTY_DEMO_DATA", List.of(), List.of()));
        }

        List<FetchAndSaveResult> fetchAndSaveResults = new ArrayList<>();
        List<TestPipelineResult.TopicResult> allTopics = new ArrayList<>();
        List<SummarizeResponse> allSummaries = new ArrayList<>();
        LocalDateTime lastExecutedAt = LocalDateTime.now();

        for (DemoDataBatch batch : batches) {
            DemoPipelineResult batchResult = runDemoBatch(batch);
            fetchAndSaveResults.add(batchResult.fetchAndSave());
            allTopics.addAll(batchResult.topics());
            allSummaries.addAll(batchResult.summaries());
            lastExecutedAt = batchResult.executedAt();
        }

        return TestPipelineResult.builder()
                .fetchAndSave(mergeFetchAndSaveResults(fetchAndSaveResults, lastExecutedAt))
                .topics(allTopics)
                .summaries(allSummaries)
                .executedAt(lastExecutedAt)
                .build();
    }

    private DemoPipelineResult runDemoBatch(DemoDataBatch batch) {
        LocalDateTime startedAt = LocalDateTime.now();
        String batchName = batch.historyTargetName();

        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .status(PipelineStatus.RUNNING)
                .fetchedCount(0).savedCount(0).clusteredCount(0).summarizedCount(0)
                .message(batch.date() == null ? "데모 데이터 로드 중" : "데모 데이터 로드 중: " + batch.date())
                .startedAt(startedAt)
                .build());

        log.info("[DemoLoad] batch={} JSON에서 읽은 기사 수: {}, feedErrors={}",
                batchName, batch.candidates().size(), batch.feedErrors().size());

        Map<String, List<ArticleCandidate>> bySource = batch.candidates().stream()
                .collect(Collectors.groupingBy(ArticleCandidate::getPublisherCode));

        List<FeedCollectResult> feedResults = bySource.entrySet().stream()
                .map(e -> FeedCollectResult.success(FeedSource.valueOf(e.getKey()), e.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));

        for (DemoFeedError error : batch.feedErrors()) {
            toFailedFeedResult(error).ifPresent(feedResults::add);
        }

        FetchAndSaveResult fetchAndSave = articleSaveService.fetchAndSaveFromResults(feedResults);
        LocalDateTime step1FinishedAt = LocalDateTime.now();
        log.info("[DemoLoad Step 1] batch={} saved={} duplicates={}",
                batchName, fetchAndSave.getSavedCount(), fetchAndSave.getDuplicateCount());

        saveDemoFetchAndSaveSteps(run, fetchAndSave, batchName, startedAt, step1FinishedAt);

        DemoClusteringResult clustering = runDemoClustering();
        LocalDateTime step2FinishedAt = LocalDateTime.now();
        log.info("[DemoLoad Step 2] batch={} topics={}", batchName, clustering.topics().size());

        saveDemoClusteringSteps(run, clustering.clusteringResults(), step1FinishedAt, step2FinishedAt);

        List<SummarizeResponse> summaries = aiSummaryService.summarizeAll();
        LocalDateTime finishedAt = LocalDateTime.now();
        log.info("[DemoLoad Step 3] batch={} summaries={}", batchName, summaries.size());

        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.AI_SUMMARY)
                .status(PipelineStatus.SUCCESS)
                .targetType("TOPIC")
                .targetName("ACTIVE_TOPICS")
                .processedCount(summaries.size())
                .successCount(summaries.size())
                .failedCount(0)
                .message("AI 요약 생성 완료")
                .startedAt(step2FinishedAt)
                .finishedAt(finishedAt)
                .build());

        saveDemoCompletedStep(run, batchName, finishedAt);

        int clustersCreated = clustering.clusteringResults().stream()
                .mapToInt(ClusteringResult::getClustersCreated)
                .sum();
        run.complete(
                statusFromCounts(fetchAndSave.getSavedCount(), fetchAndSave.getFailedCount()),
                fetchAndSave.getFetchedCount(),
                fetchAndSave.getSavedCount(),
                clustersCreated,
                summaries.size(),
                batch.date() == null ? "데모 데이터 로드 완료" : "데모 데이터 로드 완료: " + batch.date(),
                null,
                finishedAt
        );
        pipelineRunRepository.save(run);

        return new DemoPipelineResult(fetchAndSave, clustering.topics(), summaries, finishedAt);
    }

    private void saveDemoFetchAndSaveSteps(PipelineRun run, FetchAndSaveResult result, String batchName,
                                           LocalDateTime startedAt, LocalDateTime finishedAt) {
        LocalDateTime executedAt = result.getExecutedAt() != null ? result.getExecutedAt() : finishedAt;
        int articleSaveFailedCount = articleSaveFailedCount(result);

        if (result.getFeeds() == null || result.getFeeds().isEmpty()) {
            pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                    .pipelineRun(run)
                    .step(PipelineStep.RSS_COLLECT)
                    .status(statusFromCounts(result.getFetchedCount(), result.getFailedCount()))
                    .targetType("DEMO_JSON")
                    .targetName(batchName)
                    .processedCount(result.getFetchedCount())
                    .successCount(result.getFetchedCount())
                    .failedCount(result.getFailedCount())
                    .errorType(result.getFailedCount() > 0 ? "DEMO_RSS_COLLECT_FAILED" : null)
                    .errorMessage(result.getFailedCount() > 0 ? "데모 JSON 수집/저장 단계가 실패했습니다." : null)
                    .message(result.getFailedCount() > 0 ? "RSS 수집/저장 실패" : "RSS 수집 완료")
                    .startedAt(startedAt)
                    .finishedAt(executedAt)
                    .build());
        } else {
            pipelineStepHistoryRepository.saveAll(result.getFeeds().stream()
                    .map(feed -> PipelineStepHistory.builder()
                            .pipelineRun(run)
                            .step(PipelineStep.RSS_COLLECT)
                            .status(feed.isCollectSuccess() ? PipelineStatus.SUCCESS : PipelineStatus.FAILED)
                            .targetType("PUBLISHER_FEED")
                            .targetName(feed.getPublisherName())
                            .processedCount(feed.getFetched())
                            .successCount(feed.isCollectSuccess() ? feed.getFetched() : 0)
                            .failedCount(feed.isCollectSuccess() ? 0 : 1)
                            .errorType(feed.isCollectSuccess() ? null : feed.getErrorType())
                            .errorMessage(feed.getErrorMessage())
                            .message(feed.isCollectSuccess() ? "RSS 피드 수집 완료" : "RSS 피드 수집 실패")
                            .startedAt(startedAt)
                            .finishedAt(executedAt)
                            .build())
                    .toList());
        }

        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.ARTICLE_SAVE)
                .status(statusFromCounts(result.getSavedCount(), articleSaveFailedCount))
                .targetType("ARTICLE")
                .targetName("NORMALIZED_ARTICLES")
                .processedCount(result.getSavedCount() + articleSaveFailedCount)
                .successCount(result.getSavedCount())
                .failedCount(articleSaveFailedCount)
                .errorType(articleSaveFailedCount > 0 ? "ARTICLE_SAVE_PARTIAL_FAILED" : null)
                .errorMessage(articleSaveFailedCount > 0 ? articleSaveFailedCount + "건의 기사 저장에 실패했습니다." : null)
                .message(articleSaveFailedCount > 0
                        ? String.format("기사 저장 부분 실패 (저장 %d건, 실패 %d건)", result.getSavedCount(), articleSaveFailedCount)
                        : String.format("기사 저장 완료 (저장 %d건)", result.getSavedCount()))
                .startedAt(executedAt)
                .finishedAt(executedAt)
                .build());

        if (result.getValidationErrors() != null && !result.getValidationErrors().isEmpty()) {
            pipelineStepHistoryRepository.saveAll(result.getValidationErrors().stream()
                    .map(err -> PipelineStepHistory.builder()
                            .pipelineRun(run)
                            .step(PipelineStep.ARTICLE_SAVE)
                            .status(PipelineStatus.FAILED)
                            .targetType("ARTICLE")
                            .targetName(err.getTargetName())
                            .processedCount(0)
                            .successCount(0)
                            .failedCount(1)
                            .errorType(err.getErrorType())
                            .errorMessage(err.getErrorMessage())
                            .message("필수 필드 누락으로 기사 저장 건너뜀")
                            .startedAt(executedAt)
                            .finishedAt(executedAt)
                            .build())
                    .toList());
        }
    }

    private int articleSaveFailedCount(FetchAndSaveResult result) {
        if (result.getFeeds() == null || result.getFeeds().isEmpty()) {
            return result.getValidationErrors() == null ? 0 : result.getValidationErrors().size();
        }
        return result.getFeeds().stream()
                .filter(FetchAndSaveResult.FeedSummary::isCollectSuccess)
                .mapToInt(FetchAndSaveResult.FeedSummary::getFailed)
                .sum();
    }

    private PipelineStatus statusFromCounts(int successCount, int failedCount) {
        if (failedCount > 0 && successCount > 0) {
            return PipelineStatus.PARTIAL_FAILED;
        }
        if (failedCount > 0) {
            return PipelineStatus.FAILED;
        }
        return PipelineStatus.SUCCESS;
    }

    private void saveDemoClusteringSteps(PipelineRun run, List<ClusteringResult> results,
                                         LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (results.isEmpty()) {
            pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                    .pipelineRun(run)
                    .step(PipelineStep.TOPIC_CLUSTERING)
                    .status(PipelineStatus.SUCCESS)
                    .targetType("ARTICLE_CATEGORY")
                    .targetName("ALL_CATEGORIES")
                    .processedCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .message("토픽 클러스터링 완료")
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .build());
            return;
        }

        pipelineStepHistoryRepository.saveAll(results.stream()
                .map(result -> PipelineStepHistory.builder()
                        .pipelineRun(run)
                        .step(PipelineStep.TOPIC_CLUSTERING)
                        .status(PipelineStatus.SUCCESS)
                        .targetType("ARTICLE_CATEGORY")
                        .targetName(result.getCategory().name())
                        .processedCount(result.getClustersCreated())
                        .successCount(result.getClustersCreated())
                        .failedCount(0)
                        .message("토픽 클러스터링 완료")
                        .startedAt(result.getExecutedAt() != null ? result.getExecutedAt() : startedAt)
                        .finishedAt(result.getExecutedAt() != null ? result.getExecutedAt() : finishedAt)
                        .build())
                .toList());
    }

    private void saveDemoCompletedStep(PipelineRun run, String batchName, LocalDateTime finishedAt) {
        pipelineStepHistoryRepository.save(PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.COMPLETED)
                .status(PipelineStatus.SUCCESS)
                .targetType("PIPELINE")
                .targetName(batchName)
                .processedCount(0)
                .successCount(0)
                .failedCount(0)
                .message("파이프라인 실행 완료")
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .build());
    }

    private FetchAndSaveResult mergeFetchAndSaveResults(List<FetchAndSaveResult> results, LocalDateTime executedAt) {
        int fetched = results.stream().mapToInt(FetchAndSaveResult::getFetchedCount).sum();
        int saved = results.stream().mapToInt(FetchAndSaveResult::getSavedCount).sum();
        int duplicate = results.stream().mapToInt(FetchAndSaveResult::getDuplicateCount).sum();
        int failed = results.stream().mapToInt(FetchAndSaveResult::getFailedCount).sum();
        List<FetchAndSaveResult.FeedSummary> feeds = results.stream()
                .filter(result -> result.getFeeds() != null)
                .flatMap(result -> result.getFeeds().stream())
                .toList();

        return FetchAndSaveResult.builder()
                .fetchedCount(fetched)
                .savedCount(saved)
                .duplicateCount(duplicate)
                .failedCount(failed)
                .feeds(feeds)
                .executedAt(executedAt)
                .build();
    }

    private List<DemoDataBatch> readDemoJsonBatches() {
        File dir = new File(demoDataPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("demo-data 디렉토리가 없습니다: {}", dir.getAbsolutePath());
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            log.warn("demo-data 디렉토리에 JSON 파일이 없습니다: {}", dir.getAbsolutePath());
            return List.of();
        }

        Arrays.sort(jsonFiles, Comparator.comparing(File::getName));

        List<DemoDataBatch> batches = new ArrayList<>();
        for (File file : jsonFiles) {
            try {
                DemoDataPayload payload = readDemoDataPayload(mapper, file);
                log.info("JSON 읽기 완료 file={} count={} feedErrors={}",
                        file.getName(), payload.articles().size(), payload.feedErrors().size());
                batches.add(new DemoDataBatch(parseDateFromFileName(file), file.getName(),
                        payload.articles(), payload.feedErrors()));
            } catch (Exception e) {
                log.error("JSON 읽기 실패 file={} cause={}", file.getName(), e.getMessage(), e);
            }
        }
        return batches;
    }

    private DemoDataPayload readDemoDataPayload(ObjectMapper mapper, File file) throws java.io.IOException {
        JsonNode root = mapper.readTree(file);
        if (root.isArray()) {
            List<ArticleCandidate> articles = mapper.convertValue(
                    root,
                    mapper.getTypeFactory().constructCollectionType(List.class, ArticleCandidate.class));
            return new DemoDataPayload(articles, List.of());
        }

        JsonNode articlesNode = root.path("articles");
        List<ArticleCandidate> articles = articlesNode.isArray()
                ? mapper.convertValue(
                        articlesNode,
                        mapper.getTypeFactory().constructCollectionType(List.class, ArticleCandidate.class))
                : List.of();

        JsonNode feedErrorsNode = root.path("feedErrors");
        List<DemoFeedError> feedErrors = feedErrorsNode.isArray()
                ? mapper.convertValue(
                        feedErrorsNode,
                        mapper.getTypeFactory().constructCollectionType(List.class, DemoFeedError.class))
                : List.of();

        return new DemoDataPayload(articles, feedErrors);
    }

    private java.util.Optional<FeedCollectResult> toFailedFeedResult(DemoFeedError error) {
        FeedSource source;
        try {
            source = FeedSource.valueOf(error.feedSourceCode());
        } catch (Exception e) {
            log.warn("알 수 없는 demo feedError feedSourceCode: {}", error.feedSourceCode());
            return java.util.Optional.empty();
        }
        String errorType = error.errorType() == null || error.errorType().isBlank()
                ? "RSS_FEED_ERROR"
                : error.errorType();
        String errorMessage = error.errorMessage() == null || error.errorMessage().isBlank()
                ? "RSS 피드 수집 실패"
                : error.errorMessage();
        return java.util.Optional.of(FeedCollectResult.failure(source, errorType, errorMessage));
    }

    private LocalDate parseDateFromFileName(File file) {
        String name = file.getName();
        if (!name.endsWith(".json")) {
            return null;
        }
        try {
            return LocalDate.parse(name.substring(0, name.length() - ".json".length()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 모든 카테고리를 클러스터링하고, 생성된 토픽에 연결된 기사 목록을 조회해 반환한다.
     * 기사마다 TfIdfVectorizer.tokenize() 결과를 포함한다.
     */
    private List<TestPipelineResult.TopicResult> buildTopicResults() {
        return runDemoClustering().topics();
    }

    private DemoClusteringResult runDemoClustering() {
        List<ClusteringResult> clusteringResults = new ArrayList<>();
        List<TestPipelineResult.TopicResult> topics = Arrays.stream(ArticleCategory.values())
                .flatMap(category -> {
                    try {
                        ClusteringResult result = topicClusteringService.cluster(
                                new ClusteringRequest(category, 24));
                        clusteringResults.add(result);
                        return result.getTopics().stream()
                                .map(s -> toTopicResult(s.getTopicId(), s.getTitle()));
                    } catch (Exception e) {
                        log.error("[Test Step 2] 클러스터링 실패 category={} cause={}", category, e.getMessage(), e);
                        return Stream.empty();
                    }
                })
                .toList();
        return new DemoClusteringResult(clusteringResults, topics);
    }

    private TestPipelineResult.TopicResult toTopicResult(Long topicId, String title) {
        List<TopicArticle> topicArticles = topicArticleRepository.findByTopicIdWithDetails(topicId);

        List<TestPipelineResult.ArticleInfo> articleInfos = topicArticles.stream()
                .map(ta -> {
                    String headline = ta.getArticle().getHeadline();
                    return TestPipelineResult.ArticleInfo.builder()
                            .publisher(ta.getArticle().getPublisher().getName())
                            .country(ta.getArticle().getPublisher().getCountry().name())
                            .politicalLeaning(ta.getArticle().getPublisher().getPoliticalLeaning().name())
                            .headline(headline)
                            .normalizedHeadline(ta.getArticle().getNormalizedHeadline())
                            .tokens(TfIdfVectorizer.tokenize(headline))
                            .description(ta.getArticle().getDescription())
                            .build();
                })
                .toList();

        return TestPipelineResult.TopicResult.builder()
                .topicId(topicId)
                .title(title)
                .articles(articleInfos)
                .build();
    }

    private record DemoDataPayload(List<ArticleCandidate> articles, List<DemoFeedError> feedErrors) {
    }

    private record DemoFeedError(String feedSourceCode, String publisherName, String errorType, String errorMessage) {
    }

    private record DemoDataBatch(LocalDate date, String fileName, List<ArticleCandidate> candidates,
                                 List<DemoFeedError> feedErrors) {
        private String historyTargetName() {
            return date != null ? date.toString() : fileName;
        }
    }

    private record DemoClusteringResult(
            List<ClusteringResult> clusteringResults,
            List<TestPipelineResult.TopicResult> topics
    ) {
    }

    private record DemoPipelineResult(
            FetchAndSaveResult fetchAndSave,
            List<TestPipelineResult.TopicResult> topics,
            List<SummarizeResponse> summaries,
            LocalDateTime executedAt
    ) {
    }
}
