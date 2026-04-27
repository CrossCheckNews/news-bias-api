package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.util.TfIdfVectorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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

    public TestPipelineResult run() {
        // Step 1: fixture XML 읽기 → 정규화 → DB 저장
        FetchAndSaveResult fetchAndSave = articleSaveService.fetchAndSave(null);
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
