package com.crosschecknews.api.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gemini를 먼저 시도하고, 실패하면 Ollama로 폴백한다.
 *
 * <p>폴백 트리거 조건:
 * <ul>
 *   <li>GEMINI_API_KEY가 설정되지 않은 경우</li>
 *   <li>Gemini 호출 중 GeminiException 발생 (429, 500 등)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackAiClient implements AiClient {

    private final GeminiClient geminiClient;
    private final OllamaClient ollamaClient;

    private String lastUsedModel;

    @Override
    public String generate(String prompt) {
        // TODO: AI 호출 비활성화 (개발 중) — 실제 호출 시 아래 주석 해제
        log.info("AI 호출 비활성화 상태 — stub 응답 반환");
        lastUsedModel = "stub";
        return "[AI 요약 비활성화]";

        /*
        if (geminiClient.isAvailable()) {
            try {
                String result = geminiClient.generate(prompt);
                lastUsedModel = geminiClient.getModel();
                return result;
            } catch (GeminiException e) {
                log.warn("Gemini 호출 실패, Ollama로 폴백. reason={}", e.getMessage());
            }
        } else {
            log.info("GEMINI_API_KEY 미설정 → Ollama로 바로 처리");
        }

        String result = ollamaClient.generate(prompt);
        lastUsedModel = ollamaClient.getModel();
        return result;
        */
    }

    @Override
    public String getModel() {
        return lastUsedModel != null ? lastUsedModel : geminiClient.getModel();
    }
}
