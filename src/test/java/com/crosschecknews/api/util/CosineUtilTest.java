package com.crosschecknews.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CosineUtilTest {

    @Test
    void 동일_벡터는_유사도_1() {
        double[] v = {1.0, 2.0, 3.0};
        assertThat(CosineUtil.cosine(v, v)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void 직교_벡터는_유사도_0() {
        double[] v1 = {1.0, 0.0};
        double[] v2 = {0.0, 1.0};
        assertThat(CosineUtil.cosine(v1, v2)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 반대_방향_벡터는_유사도_음수() {
        double[] v1 = {1.0, 0.0};
        double[] v2 = {-1.0, 0.0};
        assertThat(CosineUtil.cosine(v1, v2)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void 일반_벡터의_유사도는_0과_1_사이() {
        double[] v1 = {1.0, 2.0, 3.0};
        double[] v2 = {1.0, 2.0, 4.0};
        double sim = CosineUtil.cosine(v1, v2);
        assertThat(sim).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    void 영벡터가_포함되면_유사도_0() {
        double[] zero = {0.0, 0.0, 0.0};
        double[] v    = {1.0, 2.0, 3.0};
        assertThat(CosineUtil.cosine(zero, v)).isEqualTo(0.0);
        assertThat(CosineUtil.cosine(v, zero)).isEqualTo(0.0);
    }

    @Test
    void null_벡터는_유사도_0() {
        double[] v = {1.0, 2.0};
        assertThat(CosineUtil.cosine(null, v)).isEqualTo(0.0);
        assertThat(CosineUtil.cosine(v, null)).isEqualTo(0.0);
        assertThat(CosineUtil.cosine(null, null)).isEqualTo(0.0);
    }

    @Test
    void 차원이_다른_벡터는_예외발생() {
        double[] v1 = {1.0, 2.0};
        double[] v2 = {1.0, 2.0, 3.0};
        assertThatThrownBy(() -> CosineUtil.cosine(v1, v2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("벡터 차원 불일치");
    }

    @Test
    void 스케일이_달라도_유사도는_동일() {
        double[] v1 = {1.0, 2.0, 3.0};
        double[] v2 = {2.0, 4.0, 6.0}; // v1 × 2
        assertThat(CosineUtil.cosine(v1, v2)).isCloseTo(1.0, within(1e-9));
    }
}
