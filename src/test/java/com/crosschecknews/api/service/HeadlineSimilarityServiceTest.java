package com.crosschecknews.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 1~3 핵심 로직: Jaccard 유사도 기반 헤드라인 유사도 테스트.
 * 외부 의존성 없이 순수 로직만 검증.
 */
class HeadlineSimilarityServiceTest {

    private final HeadlineSimilarityService service = new HeadlineSimilarityService();

    @Test
    void 동일한_헤드라인은_유사도_1() {
        double sim = service.similarity(
                "trump wins us election",
                "trump wins us election"
        );
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void 완전히_다른_헤드라인은_유사도_0() {
        double sim = service.similarity(
                "trump wins election",
                "japan earthquake tsunami"
        );
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void 단어_일부_겹치면_유사도가_0과_1_사이() {
        // "trump" "election" 공유
        double sim = service.similarity(
                "trump wins us election",
                "trump loses presidential election"
        );
        assertThat(sim).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    void 클러스터링_임계값_0_25_이상이면_동일_이슈로_판단_가능() {
        // 동일 이슈를 다루는 두 헤드라인
        double sim = service.similarity(
                "north korea missile launch threatens region",
                "north korea fires missile amid tensions"
        );
        // "north", "korea", "missile" 공유 → Jaccard 0.25 이상 기대
        assertThat(sim).isGreaterThanOrEqualTo(0.25);
    }

    @Test
    void 불용어만_있는_헤드라인은_유사도_계산에서_토큰이_제거된다() {
        // "is", "the", "a" 등 불용어만 있으면 토큰 없음 → 0.0
        double sim = service.similarity(
                "is the a an",
                "trump wins election"
        );
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void 둘_다_빈_문자열이면_유사도_1() {
        assertThat(service.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    void 한쪽만_빈_문자열이면_유사도_0() {
        assertThat(service.similarity("", "trump wins")).isEqualTo(0.0);
        assertThat(service.similarity("trump wins", "")).isEqualTo(0.0);
    }

    @Test
    void null_입력은_빈_문자열과_동일하게_처리된다() {
        assertThat(service.similarity(null, null)).isEqualTo(1.0);
        assertThat(service.similarity(null, "trump wins")).isEqualTo(0.0);
    }

    @Test
    void 두_글자_미만_토큰은_무시된다() {
        // "i" 는 길이 1 → 무시
        double sim = service.similarity("i go", "i go");
        // "go" 는 길이 2로 유효 → 유사도 1.0
        assertThat(sim).isEqualTo(1.0);
    }
}
