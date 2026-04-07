package com.crosschecknews.api.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HeadlineSimilarityService {

    private static final Set<String> STOPWORDS = Set.of(
            // 영어
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "can", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "about", "after",
            "before", "than", "that", "this", "it", "its", "he", "she", "they",
            "we", "not", "no", "or", "and", "but", "if", "up", "over", "out",
            "says", "say", "said", "new", "more", "amid",
            // 한국어 조사/어미
            "은", "는", "이", "가", "을", "를", "에", "의", "로", "와", "과",
            "도", "에서", "에게", "으로", "이다", "하다", "있다", "없다", "등", "및"
    );

    private static final int MIN_TOKEN_LENGTH = 2;

    /**
     * 두 정규화 헤드라인 사이의 Jaccard 유사도 (0.0 ~ 1.0)
     */
    public double similarity(String h1, String h2) {
        Set<String> t1 = tokenize(h1);
        Set<String> t2 = tokenize(h2);

        if (t1.isEmpty() && t2.isEmpty()) return 1.0;
        if (t1.isEmpty() || t2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(t1);
        intersection.retainAll(t2);

        Set<String> union = new HashSet<>(t1);
        union.addAll(t2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 정규화 헤드라인 → 유효 토큰 Set
     * - 공백/특수문자 기준 분리
     * - 최소 길이 2 이상
     * - 불용어 제거
     */
    public Set<String> tokenize(String normalizedHeadline) {
        if (normalizedHeadline == null || normalizedHeadline.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalizedHeadline.split("[\\s\\p{Punct}]+"))
                .map(String::toLowerCase)
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .filter(t -> !STOPWORDS.contains(t))
                .collect(Collectors.toSet());
    }
}
