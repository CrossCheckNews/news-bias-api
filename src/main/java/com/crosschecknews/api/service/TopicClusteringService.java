package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ClusteringRequest;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import com.crosschecknews.api.util.CosineUtil;
import com.crosschecknews.api.util.TfIdfVectorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicClusteringService {

    @Value("${clustering.similarity-threshold:0.78}")
    private double similarityThreshold;

    @Value("${clustering.min-publishers:2}")
    private int minPublishers;

    private final ArticleRepository         articleRepository;
    private final TopicRepository           topicRepository;
    private final TopicArticleRepository    topicArticleRepository;

    @Transactional
    public ClusteringResult cluster(ClusteringRequest request) {
        LocalDateTime since = LocalDateTime.now().minusHours(request.getFromHours());

        // 1. 후보 기사 조회
        List<Article> candidates = articleRepository.findClusteringCandidates(request.getCategory(), since);
        log.info("클러스터링 시작 category={} fromHours={} candidates={} threshold={}",
                request.getCategory(), request.getFromHours(), candidates.size(), similarityThreshold);

        if (candidates.size() < minPublishers) {
            log.info("후보 기사 부족 ({}개) → 클러스터링 생략", candidates.size());
            return emptyResult(request, candidates.size());
        }

        // 2. TF-IDF 벡터 계산 (코퍼스 전체 기준으로 IDF 산출)
        Map<Long, double[]> tfidfVectors = computeTfIdf(candidates);
        log.info("TF-IDF 벡터화 완료 articles={}", candidates.size());

        // 3. Union-Find 클러스터링
        List<List<Article>> clusters = buildClusters(candidates, tfidfVectors);

        // 4. 유효 클러스터 필터
        List<List<Article>> validClusters = clusters.stream()
                .filter(this::isValidCluster)
                .toList();

        log.info("클러스터 생성 완료 total={} valid={}", clusters.size(), validClusters.size());

        // 5. Topic + TopicArticle 저장
        int linkedCount = 0;
        List<ClusteringResult.TopicSummary> summaries = new ArrayList<>();

        for (List<Article> cluster : validClusters) {
            Topic topic = createTopic(cluster, request.getCategory(), tfidfVectors);
            topicRepository.save(topic);

            List<String> publishers = new ArrayList<>();
            for (Article article : cluster) {
                if (!topicArticleRepository.existsByTopicIdAndArticleId(topic.getId(), article.getId())) {
                    topicArticleRepository.save(TopicArticle.builder()
                            .topic(topic)
                            .article(article)
                            .build());
                    linkedCount++;
                }
                publishers.add(article.getPublisher().getName());
            }

            long distinctPublishers = cluster.stream()
                    .map(a -> a.getPublisher().getId())
                    .distinct().count();

            summaries.add(ClusteringResult.TopicSummary.builder()
                    .topicId(topic.getId())
                    .title(topic.getTitle())
                    .articleCount(cluster.size())
                    .publisherCount((int) distinctPublishers)
                    .publishers(publishers.stream().distinct().toList())
                    .build());

            log.info("Topic 생성 id={} title='{}' articles={} publishers={}",
                    topic.getId(), topic.getTitle(), cluster.size(), distinctPublishers);
        }

        log.info("클러스터링 완료 topicsCreated={} linkedArticles={}", validClusters.size(), linkedCount);

        return ClusteringResult.builder()
                .category(request.getCategory())
                .fromHours(request.getFromHours())
                .scannedArticleCount(candidates.size())
                .clustersCreated(validClusters.size())
                .linkedArticleCount(linkedCount)
                .topics(summaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    // ── TF-IDF 벡터화 ─────────────────────────────────────────────────────────

    /**
     * 후보 기사 전체를 코퍼스로 삼아 TF-IDF 벡터를 계산한다.
     * IDF는 코퍼스 전체 기준이므로, 기사가 많을수록 희귀 단어의 가중치가 높아진다.
     *
     * @return articleId → TF-IDF 벡터
     */
    private Map<Long, double[]> computeTfIdf(List<Article> articles) {
        List<String> comparisonTexts = articles.stream()
                .map(a -> {
                    String desc = a.getDescription();
                    return (desc != null && !desc.isBlank())
                            ? a.getHeadline() + " " + desc
                            : a.getHeadline();
                })
                .toList();

        double[][] vectors = TfIdfVectorizer.vectorize(comparisonTexts);

        Map<Long, double[]> result = new HashMap<>();
        for (int i = 0; i < articles.size(); i++) {
            result.put(articles.get(i).getId(), vectors[i]);
        }
        return result;
    }

    // ── 클러스터 생성 (Union-Find) ─────────────────────────────────────────────

    private List<List<Article>> buildClusters(List<Article> articles, Map<Long, double[]> embeddings) {
        int n = articles.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        int pairCount = 0, unionCount = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                pairCount++;
                double sim = CosineUtil.cosine(
                        embeddings.get(articles.get(i).getId()),
                        embeddings.get(articles.get(j).getId())
                );
                if (sim >= similarityThreshold) {
                    union(parent, i, j);
                    unionCount++;
                }
            }
        }

        log.info("쌍 비교 완료 pairs={} unions={}", pairCount, unionCount);

        Map<Integer, List<Article>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(articles.get(i));
        }

        return new ArrayList<>(groups.values());
    }

    private int find(int[] parent, int x) {
        if (parent[x] != x) parent[x] = find(parent, parent[x]);
        return parent[x];
    }

    private void union(int[] parent, int x, int y) {
        parent[find(parent, x)] = find(parent, y);
    }

    // ── 유효성 검사 ────────────────────────────────────────────────────────────

    private boolean isValidCluster(List<Article> cluster) {
        long distinctPublishers = cluster.stream()
                .map(a -> a.getPublisher().getId())
                .distinct().count();
        return distinctPublishers >= minPublishers;
    }

    // ── Topic 생성 ─────────────────────────────────────────────────────────────

    private Topic createTopic(List<Article> cluster, Category category, Map<Long, double[]> embeddings) {
        String title = pickRepresentativeTitle(cluster, embeddings);
        return Topic.builder()
                .title(title)
                .category(category)
                .status(TopicStatus.ACTIVE)
                .startDate(cluster.stream()
                        .map(a -> a.getPublishedAt() != null
                                ? a.getPublishedAt().toLocalDate()
                                : a.getFetchedAt().toLocalDate())
                        .min(Comparator.naturalOrder())
                        .orElse(LocalDateTime.now().toLocalDate()))
                .build();
    }

    /**
     * 클러스터 내 다른 기사들과 cosine similarity 합이 가장 높은 기사(centroid)의 원본 headline을 Topic 제목으로 사용.
     */
    private String pickRepresentativeTitle(List<Article> cluster, Map<Long, double[]> embeddings) {
        if (cluster.size() == 1) return cluster.get(0).getHeadline();

        return cluster.stream()
                .max(Comparator.comparingDouble(article ->
                        cluster.stream()
                                .filter(other -> !other.getId().equals(article.getId()))
                                .mapToDouble(other -> CosineUtil.cosine(
                                        embeddings.get(article.getId()),
                                        embeddings.get(other.getId())))
                                .sum()
                ))
                .map(Article::getHeadline)
                .orElse(cluster.get(0).getHeadline());
    }

    private ClusteringResult emptyResult(ClusteringRequest request, int scanned) {
        return ClusteringResult.builder()
                .category(request.getCategory())
                .fromHours(request.getFromHours())
                .scannedArticleCount(scanned)
                .clustersCreated(0)
                .linkedArticleCount(0)
                .topics(List.of())
                .executedAt(LocalDateTime.now())
                .build();
    }
}
