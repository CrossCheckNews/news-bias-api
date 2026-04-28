package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.FeedSource;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.util.TfIdfVectorizer;
import com.fasterxml.jackson.databind.DeserializationFeature;
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

    private final ArticleSaveService     articleSaveService;
    private final TopicClusteringService topicClusteringService;
    private final AiSummaryService       aiSummaryService;
    private final TopicArticleRepository topicArticleRepository;
    private final RssCollectService      rssCollectService;

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
     * demo-data/ 디렉토리의 모든 JSON 파일을 읽어 Article 저장 → 클러스터링 → AI 요약을 실행한다.
     */
    public TestPipelineResult loadFromDemoData() {
        List<ArticleCandidate> allCandidates = readAllDemoJson();
        log.info("[DemoLoad] JSON에서 읽은 기사 수: {}", allCandidates.size());

        Map<String, List<ArticleCandidate>> bySource = allCandidates.stream()
                .collect(Collectors.groupingBy(ArticleCandidate::getPublisherCode));

        List<FeedCollectResult> feedResults = bySource.entrySet().stream()
                .map(e -> FeedCollectResult.success(FeedSource.valueOf(e.getKey()), e.getValue()))
                .toList();

        FetchAndSaveResult fetchAndSave = articleSaveService.fetchAndSaveFromResults(feedResults);
        log.info("[DemoLoad Step 1] saved={} duplicates={}", fetchAndSave.getSavedCount(), fetchAndSave.getDuplicateCount());

        List<TestPipelineResult.TopicResult> topics = buildTopicResults();
        log.info("[DemoLoad Step 2] topics={}", topics.size());

        List<SummarizeResponse> summaries = aiSummaryService.summarizeAll();
        log.info("[DemoLoad Step 3] summaries={}", summaries.size());

        return TestPipelineResult.builder()
                .fetchAndSave(fetchAndSave)
                .topics(topics)
                .summaries(summaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private List<ArticleCandidate> readAllDemoJson() {
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

        List<ArticleCandidate> all = new ArrayList<>();
        for (File file : jsonFiles) {
            try {
                List<ArticleCandidate> candidates = mapper.readValue(
                        file,
                        mapper.getTypeFactory().constructCollectionType(List.class, ArticleCandidate.class));
                log.info("JSON 읽기 완료 file={} count={}", file.getName(), candidates.size());
                all.addAll(candidates);
            } catch (Exception e) {
                log.error("JSON 읽기 실패 file={} cause={}", file.getName(), e.getMessage(), e);
            }
        }
        return all;
    }

    /**
     * 모든 카테고리를 클러스터링하고, 생성된 토픽에 연결된 기사 목록을 조회해 반환한다.
     * 기사마다 TfIdfVectorizer.tokenize() 결과를 포함한다.
     */
    private List<TestPipelineResult.TopicResult> buildTopicResults() {
        return Arrays.stream(ArticleCategory.values())
                .flatMap(category -> {
                    try {
                        ClusteringResult result = topicClusteringService.cluster(
                                new ClusteringRequest(category, 24));
                        return result.getTopics().stream()
                                .map(s -> toTopicResult(s.getTopicId(), s.getTitle()));
                    } catch (Exception e) {
                        log.error("[Test Step 2] 클러스터링 실패 category={} cause={}", category, e.getMessage(), e);
                        return Stream.empty();
                    }
                })
                .toList();
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
}
