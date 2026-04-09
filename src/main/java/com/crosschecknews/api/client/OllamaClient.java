package com.crosschecknews.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class OllamaClient implements AiClient {

    @Value("${ollama.base.url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:llama3.2}")
    private String model;

    @Value("${OLLAMA_API_KEY:}")
    private String apiKey;

    private final RestClient restClient;

    public OllamaClient() {
        this.restClient = RestClient.create();
    }

    @Override
    public String generate(String prompt) {
        OllamaRequest request = new OllamaRequest(model, prompt, false);

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(baseUrl + "/api/generate")
                .contentType(MediaType.APPLICATION_JSON);

        if (apiKey != null && !apiKey.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }

        OllamaResponse response = spec
                .body(request)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned an empty response");
        }

        log.debug("Ollama response model={} length={}", model, response.response().length());
        return response.response().strip();
    }

    @Override
    public String getModel() {
        return model;
    }

    // ── 요청/응답 record ────────────────────────────────────────────────────────

    record OllamaRequest(String model, String prompt, boolean stream) {}

    record OllamaResponse(String model, String response) {}
}
