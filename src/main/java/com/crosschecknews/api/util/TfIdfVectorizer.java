package com.crosschecknews.api.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 * TF-IDF (Term Frequency - Inverse Document Frequency) 벡터라이저.
 *
 * <h3>동작 원리</h3>
 * <ol>
 *   <li><b>토크나이징</b> — headline을 소문자로 변환 후 비알파벳/숫자 제거, 공백 분리</li>
 *   <li><b>TF 계산</b> — 각 문서에서 단어 빈도수 / 전체 단어 수 (정규화)</li>
 *   <li><b>IDF 계산</b> — log((N+1) / (df+1)) + 1 (스무딩 적용, 모든 문서에 나오는 단어도 0이 되지 않음)</li>
 *   <li><b>TF-IDF</b> — TF × IDF</li>
 *   <li><b>Cosine Similarity</b> — TF-IDF 벡터 간 코사인 유사도로 같은 이슈 여부 판별</li>
 * </ol>
 *
 * <p>외부 라이브러리 없이 순수 Java로 구현해 포트폴리오에서 직접 설명 가능한 구조.
 */
public final class TfIdfVectorizer {

    /** 알파벳/숫자/한글/한자/일본어 이외 문자는 공백으로 치환 */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{Digit}]");

    /** 단일 문자 토큰은 노이즈가 많아 제외 */
    private static final int MIN_TOKEN_LENGTH = 2;

    private TfIdfVectorizer() {}

    /**
     * 문서(headline) 목록을 TF-IDF 벡터 배열로 변환한다.
     *
     * @param docs headline 문자열 목록 (인덱스 순서 보존)
     * @return docs[i]에 대응하는 TF-IDF 벡터 배열.
     *         모든 벡터는 동일 차원(어휘 크기). 빈 headline은 영벡터.
     */
    public static double[][] vectorize(List<String> docs) {
        int n = docs.size();

        // 1. 토크나이징
        List<List<String>> tokenizedDocs = docs.stream()
                .map(TfIdfVectorizer::tokenize)
                .toList();

        // 2. 어휘(vocabulary) 구축 — 등장 순서 유지
        Set<String> vocabSet = new LinkedHashSet<>();
        for (List<String> tokens : tokenizedDocs) vocabSet.addAll(tokens);

        List<String> vocab = new ArrayList<>(vocabSet);
        Map<String, Integer> vocabIndex = new HashMap<>(vocab.size() * 2);
        for (int i = 0; i < vocab.size(); i++) vocabIndex.put(vocab.get(i), i);

        int V = vocab.size();
        if (V == 0) return new double[n][0];

        // 3. Document Frequency 계산 (각 단어가 몇 개 문서에 등장하는지)
        int[] df = new int[V];
        for (List<String> tokens : tokenizedDocs) {
            Set<String> seen = new HashSet<>(tokens);
            for (String term : seen) {
                Integer idx = vocabIndex.get(term);
                if (idx != null) df[idx]++;
            }
        }

        // 4. IDF 계산: log((N+1) / (df+1)) + 1  (스무딩)
        double[] idf = new double[V];
        for (int i = 0; i < V; i++) {
            idf[i] = Math.log((double)(n + 1) / (df[i] + 1)) + 1.0;
        }

        // 5. TF-IDF 행렬 계산
        double[][] result = new double[n][V];
        for (int d = 0; d < n; d++) {
            List<String> tokens = tokenizedDocs.get(d);
            int total = tokens.size();
            if (total == 0) continue;

            // TF: 빈도수 / 총 토큰 수
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) counts.merge(token, 1, Integer::sum);

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                Integer idx = vocabIndex.get(entry.getKey());
                if (idx == null) continue;
                double tf = (double) entry.getValue() / total;
                result[d][idx] = tf * idf[idx];
            }
        }

        return result;
    }

    /**
     * headline을 소문자로 변환하고 단어 단위 토큰 목록을 반환한다.
     *
     * <ul>
     *   <li>알파벳/숫자/한글/한자 이외 문자 → 공백 치환</li>
     *   <li>길이 1 이하 토큰 제외 (조사, 단일 기호 등 노이즈)</li>
     * </ul>
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        String cleaned = NON_WORD.matcher(text.toLowerCase()).replaceAll(" ");
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .toList();
    }
}
