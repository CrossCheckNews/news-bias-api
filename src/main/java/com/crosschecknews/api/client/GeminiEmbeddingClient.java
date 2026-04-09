package com.crosschecknews.api.client;

import com.crosschecknews.api.service.HeadlineEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Gemini text-embedding API를 사용하는 HeadlineEmbeddingService 구현체.
 * 다른 embedding API로 교체할 경우 이 클래스만 교체하면 된다.
 */
@Slf4j
@Component
public class GeminiEmbeddingClient implements HeadlineEmbeddingService {

    private static final String EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.embedding.model:gemini-embedding-001}")
    private String model;

    private final RestClient restClient;

    public GeminiEmbeddingClient() {
        // HTTP/2는 하나의 커넥션을 멀티플렉싱하므로 병렬 요청 시 GOAWAY로 전체 실패 가능
        // HTTP/1.1은 요청마다 독립 커넥션 → 병렬 안전
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @PostConstruct
    public void logConfig() {
        String masked = (apiKey != null && apiKey.length() > 8)
                ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
                : "(blank or too short)";
        log.info("[GeminiEmbeddingClient] model={} apiKey={}", model, masked);
    }

    @Override
    public double[] embed(String headline) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("GEMINI_API_KEY 환경변수가 설정되지 않았습니다");
        }

        log.debug("Embedding 요청 model={} headline='{}'", model, headline);

        // URL 경로와 body 모두에 model 지정, taskType은 유사도 비교 목적에 맞게 설정
        EmbedRequest request = new EmbedRequest(
                "models/" + model,
                new EmbedRequest.Content(List.of(new EmbedRequest.Content.Part(headline))),
                "SEMANTIC_SIMILARITY"
        );

        try {
            EmbedResponse response = restClient.post()
                    .uri(EMBED_URL, model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        throw new GeminiException(
                                "Gemini embedding API 오류 status=" + res.getStatusCode() + " body=" + body);
                    })
                    .body(EmbedResponse.class);

            if (response == null || response.embedding() == null || response.embedding().values() == null) {
                throw new GeminiException("Gemini embedding 응답이 비어있음 headline=" + headline);
            }

            List<Double> values = response.embedding().values();
            double[] vector = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }

            log.debug("Embedding 생성 완료 model={} dim={}", model, vector.length);
            return vector;

        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            // 원인 체인 전체 로깅 (root cause 확인용)
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            log.error("Embedding HTTP 오류 type={} message={} rootType={} rootMessage={}",
                    e.getClass().getSimpleName(), e.getMessage(),
                    root.getClass().getSimpleName(), root.getMessage());
            throw new GeminiException("Embedding 요청 실패: " + e.getMessage(), e);
        }
    }

    // ── 요청/응답 record ────────────────────────────────────────────────────────

    record EmbedRequest(String model, Content content, String taskType) {
        record Content(List<Part> parts) {
            record Part(String text) {}
        }
    }

    record EmbedResponse(Embedding embedding) {
        record Embedding(List<Double> values) {}
    }
}
