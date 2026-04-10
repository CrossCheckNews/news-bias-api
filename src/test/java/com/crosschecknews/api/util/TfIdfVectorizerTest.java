package com.crosschecknews.api.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TfIdfVectorizerTest {

    @Test
    void 한국어_토큰이_canonical_토큰으로_변환된다() {
        List<String> tokens = TfIdfVectorizer.tokenize("트럼프 이란 우라늄 직접 폐기 방침");
        System.out.println("Korean tokens: " + tokens);
        assertThat(tokens).contains("donald_trump", "iran", "uranium");
        assertThat(tokens).doesNotContain("트럼프", "이란", "우라늄");
    }

    @Test
    void 조사가_붙은_한국어_토큰도_변환된다() {
        List<String> tokens = TfIdfVectorizer.tokenize("네타냐후의 이스라엘에서 레바논을 공습");
        System.out.println("Particle tokens: " + tokens);
        assertThat(tokens).contains("netanyahu", "israel", "lebanon");
    }

    @Test
    void 휴전_ceasefire_공통토큰_생성() {
        List<String> korean = TfIdfVectorizer.tokenize("미·이란 휴전 합의");
        List<String> english = TfIdfVectorizer.tokenize("U.S.-Iran Cease-Fire Deal");
        System.out.println("Korean: " + korean);
        System.out.println("English: " + english);
        assertThat(korean).contains("cease_fire");
        assertThat(english).contains("cease_fire");
    }

    @Test
    void isKorean_정확히_감지() {
        assertThat(TfIdfVectorizer.isKorean("트럼프")).isTrue();
        assertThat(TfIdfVectorizer.isKorean("trump")).isFalse();
        assertThat(TfIdfVectorizer.isKorean("NUM_PCT")).isFalse();
    }

    @Test
    void stripKoreanParticle_조사_제거() {
        assertThat(TfIdfVectorizer.stripKoreanParticle("네타냐후의")).isEqualTo("네타냐후");
        assertThat(TfIdfVectorizer.stripKoreanParticle("이란에서")).isEqualTo("이란");
        assertThat(TfIdfVectorizer.stripKoreanParticle("이스라엘이")).isEqualTo("이스라엘");
        assertThat(TfIdfVectorizer.stripKoreanParticle("트럼프")).isEqualTo("트럼프"); // 조사 없으면 그대로
    }
}
