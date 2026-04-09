package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.util.CosineUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Headline 간 cosine similarity 계산 서비스.
 *
 * <p>임베딩 우선순위: DB 캐시 → 인메모리 캐시 → API 호출
 * <p>API 호출 후 벡터를 DB에 영구 저장 → 서버 재시작 후에도 재호출 없음
 */
@Slf4j
@Service
public class HeadlineSimilarityService {

    private static final TypeReference<List<Double>> LIST_DOUBLE = new TypeReference<>() {};

    private final HeadlineEmbeddingService embeddingService;
    private final HeadlinePreprocessor     preprocessor;
    private final ArticleRepository        articleRepository;
    private final ObjectMapper             objectMapper;
    private final ExecutorService          embeddingExecutor;

    /** 인메모리 캐시: key=전처리된 headline, Optional.empty()=이전 API 실패 */
    private final ConcurrentHashMap<String, Optional<double[]>> embeddingCache =
            new ConcurrentHashMap<>();

    public HeadlineSimilarityService(
            HeadlineEmbeddingService embeddingService,
            HeadlinePreprocessor preprocessor,
            ArticleRepository articleRepository,
            ObjectMapper objectMapper,
            @Value("${clustering.embedding-threads:4}") int threads
    ) {
        this.embeddingService  = embeddingService;
        this.preprocessor      = preprocessor;
        this.articleRepository = articleRepository;
        this.objectMapper      = objectMapper;
        this.embeddingExecutor = Executors.newFixedThreadPool(threads);
    }

    /**
     * 기사 목록의 embedding을 반환한다.
     *
     * <ol>
     *   <li>DB에 저장된 embeddingJson이 있으면 파싱해서 바로 사용</li>
     *   <li>없으면 API 호출 후 DB에 저장 (영구 캐시)</li>
     * </ol>
     *
     * @return articleId → embedding 벡터 (실패 기사 제외)
     */
    @Transactional
    public Map<Long, double[]> embedAll(List<Article> articles) {
        // 1. DB 캐시 분류
        List<Article> needsApi = articles.stream()
                .filter(a -> a.getEmbeddingJson() == null)
                .toList();
        long dbHits = articles.size() - needsApi.size();
        log.info("임베딩 준비 total={} dbCache={} apiNeeded={}", articles.size(), dbHits, needsApi.size());

        // 2. API 필요한 기사만 병렬 호출
        if (!needsApi.isEmpty()) {
            List<CompletableFuture<Void>> futures = needsApi.stream()
                    .map(article -> CompletableFuture.runAsync(
                            () -> fetchAndSave(article), embeddingExecutor))
                    .toList();
            futures.forEach(f -> {
                try { f.get(); } catch (Exception e) {
                    log.warn("Embedding future 실패 cause={}", e.getMessage());
                }
            });
        }

        // 3. 전체 기사에서 벡터 수집 (DB 저장된 것 포함)
        Map<Long, double[]> result = new HashMap<>();
        for (Article article : articles) {
            double[] vec = parseEmbeddingJson(article.getEmbeddingJson());
            if (vec != null) {
                result.put(article.getId(), vec);
            }
        }

        int failed = articles.size() - result.size();
        log.info("임베딩 완료 success={} failed={} (dbHit={} apiCall={})",
                result.size(), failed, dbHits, needsApi.size());
        return result;
    }

    /**
     * cosine similarity를 반환한다 (테스트 및 단독 사용).
     */
    public double similarity(String h1, String h2) {
        if (h1 == null || h1.isBlank() || h2 == null || h2.isBlank()) return 0.0;

        double[] v1 = embedSafeFromApi(h1);
        double[] v2 = embedSafeFromApi(h2);

        if (v1 == null || v2 == null) return 0.0;
        return CosineUtil.cosine(v1, v2);
    }

    @PreDestroy
    public void shutdown() {
        embeddingExecutor.shutdown();
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    /** API 호출 후 성공하면 Article.embeddingJson 업데이트 */
    private void fetchAndSave(Article article) {
        String key = preprocessor.prepare(article.getHeadline());
        double[] vec = embedSafeFromApi(key);
        if (vec == null) return;

        try {
            String json = objectMapper.writeValueAsString(vec);
            article.updateEmbedding(json);
            articleRepository.save(article);
        } catch (Exception e) {
            log.warn("Embedding JSON 직렬화 실패 articleId={} cause={}", article.getId(), e.getMessage());
        }
    }

    /** 인메모리 캐시 우선, 없으면 API 호출 */
    private double[] embedSafeFromApi(String key) {
        if (key == null || key.isBlank()) return null;

        Optional<double[]> cached = embeddingCache.get(key);
        if (cached != null) return cached.orElse(null);

        try {
            double[] vec = embeddingService.embed(key);
            embeddingCache.put(key, Optional.of(vec));
            return vec;
        } catch (Exception e) {
            log.warn("Embedding API 실패 headline='{}' cause={}", key, e.getMessage());
            embeddingCache.put(key, Optional.empty());
            return null;
        }
    }

    private double[] parseEmbeddingJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Double> values = objectMapper.readValue(json, LIST_DOUBLE);
            double[] vec = new double[values.size()];
            for (int i = 0; i < values.size(); i++) vec[i] = values.get(i);
            return vec;
        } catch (Exception e) {
            log.warn("Embedding JSON 파싱 실패 cause={}", e.getMessage());
            return null;
        }
    }
}
