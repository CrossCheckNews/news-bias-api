package com.crosschecknews.api.util;

/**
 * Cosine similarity 계산 유틸리티.
 */
public final class CosineUtil {

    private CosineUtil() {}

    /**
     * 두 벡터의 cosine similarity를 계산한다 (0.0 ~ 1.0).
     *
     * @param v1 벡터 1
     * @param v2 벡터 2
     * @return cosine similarity. 영벡터이거나 null이면 0.0
     * @throws IllegalArgumentException 벡터 차원이 다를 때
     */
    public static double cosine(double[] v1, double[] v2) {
        if (v1 == null || v2 == null) return 0.0;
        if (v1.length != v2.length) {
            throw new IllegalArgumentException(
                    "벡터 차원 불일치: v1.length=" + v1.length + " v2.length=" + v2.length);
        }

        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot   += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        // 영벡터 처리
        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
