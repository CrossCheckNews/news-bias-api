package com.crosschecknews.api.service;

/**
 * Headline → embedding 벡터 변환 인터페이스.
 * 구현체를 교체해도 클러스터링 파이프라인에 영향 없도록 인터페이스로 분리.
 */
public interface HeadlineEmbeddingService {

    /**
     * headline 문자열을 embedding 벡터로 변환한다.
     *
     * @param headline 전처리된 headline 텍스트
     * @return embedding 벡터 (double 배열)
     * @throws RuntimeException embedding API 호출 실패 시
     */
    double[] embed(String headline);
}
