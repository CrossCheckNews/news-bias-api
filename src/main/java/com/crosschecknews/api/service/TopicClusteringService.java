package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ClusteringRequest;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicClusteringService {

    /** 이 값 이상이면 동일 이슈로 판단 */
    private static final double SIMILARITY_THRESHOLD = 0.25;

    /** Topic이 되려면 최소 몇 개의 서로 다른 언론사여야 하는가 */
    private static final int MIN_PUBLISHERS = 2;

    private final ArticleRepository        articleRepository;
    private final TopicRepository          topicRepository;
    private final TopicArticleRepository   topicArticleRepository;
    private final HeadlineSimilarityService similarityService;

    @Transactional
    public ClusteringResult cluster(ClusteringRequest request) {
        LocalDateTime since = LocalDateTime.now().minusHours(request.getFromHours());

        // 1. 후보 기사 조회 (ACTIVE Topic에 미연결)
        List<Article> candidates = articleRepository.findClusteringCandidates(request.getCategory(), since);
        log.info("클러스터링 시작 category={} fromHours={} candidates={}",
                request.getCategory(), request.getFromHours(), candidates.size());

        if (candidates.size() < MIN_PUBLISHERS) {
            return emptyResult(request, candidates.size());
        }

        // 2. 유사도 기반 Union-Find 클러스터링
        List<List<Article>> clusters = buildClusters(candidates);

        // 3. 유효 클러스터 필터 (MIN_PUBLISHERS 이상 다른 언론사)
        List<List<Article>> validClusters = clusters.stream()
                .filter(this::isValidCluster)
                .toList();

        log.info("유효 클러스터 수: {}", validClusters.size());

        // 4. Topic + TopicArticle 저장
        int linkedCount = 0;
        List<ClusteringResult.TopicSummary> summaries = new ArrayList<>();

        for (List<Article> cluster : validClusters) {
            Topic topic = createTopic(cluster, request.getCategory());
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

    // ── 클러스터 생성 (Union-Find) ─────────────────────────────────────────────

    private List<List<Article>> buildClusters(List<Article> articles) {
        int n = articles.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double sim = similarityService.similarity(
                        articles.get(i).getNormalizedHeadline(),
                        articles.get(j).getNormalizedHeadline()
                );
                if (sim >= SIMILARITY_THRESHOLD) {
                    union(parent, i, j);
                }
            }
        }

        // root별로 그룹화
        Map<Integer, List<Article>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(articles.get(i));
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
        return distinctPublishers >= MIN_PUBLISHERS;
    }

    // ── Topic 생성 ─────────────────────────────────────────────────────────────

    private Topic createTopic(List<Article> cluster, Category category) {
        String title = pickRepresentativeTitle(cluster);
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
     * 클러스터 내 다른 기사들과 총 유사도가 가장 높은 기사의 헤드라인을 Topic 제목으로 사용.
     * (= 클러스터 내 centroid 기사)
     */
    private String pickRepresentativeTitle(List<Article> cluster) {
        if (cluster.size() == 1) return cluster.get(0).getHeadline();

        return cluster.stream()
                .max(Comparator.comparingDouble(article ->
                        cluster.stream()
                                .filter(other -> !other.getId().equals(article.getId()))
                                .mapToDouble(other -> similarityService.similarity(
                                        article.getNormalizedHeadline(),
                                        other.getNormalizedHeadline()))
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
