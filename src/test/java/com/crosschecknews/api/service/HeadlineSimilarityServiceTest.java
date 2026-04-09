package com.crosschecknews.api.service;

import com.crosschecknews.api.repository.ArticleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeadlineSimilarityServiceTest {

    @Mock HeadlineEmbeddingService embeddingService;
    @Mock ArticleRepository        articleRepository;

    HeadlineSimilarityService service;

    @BeforeEach
    void setUp() {
        service = new HeadlineSimilarityService(
                embeddingService, new HeadlinePreprocessor(),
                articleRepository, new ObjectMapper(), 2);
    }

    // ── cosine similarity 기반 동작 ───────────────────────────────────────────

    @Test
    void 동일한_embedding이면_유사도_1() {
        double[] vec = {1.0, 0.0, 0.0};
        given(embeddingService.embed(anyString())).willReturn(vec);

        double sim = service.similarity("trump wins election", "trump wins election");

        assertThat(sim).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void 직교_embedding이면_유사도_0() {
        given(embeddingService.embed("trump wins election")).willReturn(new double[]{1.0, 0.0});
        given(embeddingService.embed("japan earthquake tsunami")).willReturn(new double[]{0.0, 1.0});

        double sim = service.similarity("trump wins election", "japan earthquake tsunami");

        assertThat(sim).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 유사한_embedding이면_유사도가_높다() {
        given(embeddingService.embed("trump tariff trade war")).willReturn(new double[]{0.9, 0.1, 0.0});
        given(embeddingService.embed("trump trade war tariffs")).willReturn(new double[]{0.85, 0.15, 0.0});

        double sim = service.similarity("trump tariff trade war", "trump trade war tariffs");

        assertThat(sim).isGreaterThan(0.9);
    }

    // ── embedding 실패 방어 처리 ─────────────────────────────────────────────

    @Test
    void embedding_실패시_유사도_0_반환하고_배치_계속_진행() {
        given(embeddingService.embed(anyString())).willThrow(new RuntimeException("API 호출 실패"));

        double sim = service.similarity("trump wins election", "japan earthquake");

        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void 한쪽_embedding_실패시_유사도_0() {
        given(embeddingService.embed("good headline")).willReturn(new double[]{1.0, 0.0});
        given(embeddingService.embed("bad headline")).willThrow(new RuntimeException("실패"));

        double sim = service.similarity("good headline", "bad headline");

        assertThat(sim).isEqualTo(0.0);
    }

    // ── null / 빈 입력 ───────────────────────────────────────────────────────

    @Test
    void null_입력은_유사도_0() {
        double sim = service.similarity(null, "trump wins");
        assertThat(sim).isEqualTo(0.0);
        verifyNoInteractions(embeddingService);
    }

    @Test
    void 빈_문자열은_유사도_0() {
        double sim = service.similarity("", "trump wins");
        assertThat(sim).isEqualTo(0.0);
        verifyNoInteractions(embeddingService);
    }

    // ── 캐싱 동작 ────────────────────────────────────────────────────────────

    @Nested
    class EmbeddingCache {

        @Test
        void 동일_headline은_embedding_API를_한_번만_호출한다() {
            given(embeddingService.embed(anyString())).willReturn(new double[]{1.0, 0.0});

            service.similarity("trump wins election", "trump wins election different");
            service.similarity("trump wins election", "another headline");

            // "trump wins election"은 캐시 히트 → 총 embedding 호출은 3번 (3개의 고유 headline)
            verify(embeddingService, times(3)).embed(anyString());
        }

        @Test
        void 서로_다른_headline은_각각_embedding을_호출한다() {
            given(embeddingService.embed("headline a")).willReturn(new double[]{1.0, 0.0});
            given(embeddingService.embed("headline b")).willReturn(new double[]{0.0, 1.0});

            service.similarity("headline a", "headline b");

            verify(embeddingService).embed("headline a");
            verify(embeddingService).embed("headline b");
        }

        @Test
        void 동일_headline_반복_호출시_캐시에서_반환한다() {
            given(embeddingService.embed("cached headline")).willReturn(new double[]{1.0, 0.0});

            service.similarity("cached headline", "other");
            service.similarity("cached headline", "other");
            service.similarity("cached headline", "other");

            // "cached headline"은 첫 번째 호출에만 embed → 이후 캐시 사용
            verify(embeddingService, times(1)).embed("cached headline");
        }
    }
}
