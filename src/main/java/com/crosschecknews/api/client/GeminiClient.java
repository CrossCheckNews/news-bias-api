package com.crosschecknews.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class GeminiClient {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestClient restClient;

    public GeminiClient() {
        this.restClient = RestClient.create();
    }

    /**
     * 프롬프트를 Gemini에 전송하고 생성된 텍스트를 반환한다.
     */
    public String generate(String prompt) {
        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(
                        List.of(new GeminiRequest.Content.Part(prompt))
                ))
        );

        GeminiResponse response = restClient.post()
                .uri(API_URL + "?key={key}", model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null
                || response.candidates() == null
                || response.candidates().isEmpty()) {
            throw new GeminiException("Gemini returned an empty response");
        }

        List<GeminiResponse.Candidate.Content.Part> parts =
                response.candidates().get(0).content().parts();

        if (parts == null || parts.isEmpty()) {
            throw new GeminiException("Gemini returned no text parts");
        }

        String text = parts.get(0).text();
        log.debug("Gemini response model={} length={}", model, text.length());
        return text.strip();
    }

    public String getModel() {
        return model;
    }

    // ── 요청/응답 record ────────────────────────────────────────────────────────

    record GeminiRequest(List<Content> contents) {
        record Content(List<Part> parts) {
            record Part(String text) {}
        }
    }

    record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content) {
            record Content(List<Part> parts) {
                record Part(String text) {}
            }
        }
    }
}
