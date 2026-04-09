package com.crosschecknews.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class GeminiClient implements AiClient {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestClient restClient;

    public GeminiClient() {
        this.restClient = RestClient.create();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 프롬프트를 Gemini에 전송하고 생성된 텍스트를 반환한다.
     */
    @Override
    public String generate(String prompt) {
        if (!isAvailable()) {
            throw new GeminiException("Gemini API key is not configured");
        }
        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(
                        List.of(new GeminiRequest.Content.Part(prompt))
                ))
        );

        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri(API_URL + "?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new GeminiException("Gemini API rate limit exceeded (429). Please wait and retry.", e);
        } catch (HttpClientErrorException e) {
            throw new GeminiException("Gemini API error " + e.getStatusCode() + ": " + e.getMessage(), e);
        }

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

    @Override
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
