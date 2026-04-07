package com.crosschecknews.api.service;

import com.crosschecknews.api.client.GeminiClient;
import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.dto.SummarizeResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiSummaryService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;

    /**
     * 특정 Topic 1건 요약 생성 및 저장.
     */
    public SummarizeResponse summarize(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));

        return doSummarize(topic);
    }

    /**
     * aiSummary가 없는 ACTIVE Topic 일괄 요약.
     * 개별 실패는 로그만 남기고 계속 진행한다.
     */
    public List<SummarizeResponse> summarizeAll() {
        List<Topic> pending = topicRepository
                .findByAiSummaryIsNullAndStatus(TopicStatus.ACTIVE);

        log.info("일괄 요약 대상 토픽 수: {}", pending.size());

        List<SummarizeResponse> results = new ArrayList<>();
        for (Topic topic : pending) {
            try {
                results.add(doSummarize(topic));
            } catch (Exception e) {
                log.warn("토픽 요약 실패 topicId={} error={}", topic.getId(), e.getMessage());
            }
        }
        return results;
    }

    // ── 내부 ────────────────────────────────────────────────────────────────────

    private SummarizeResponse doSummarize(Topic topic) {
        List<TopicArticle> articles =
                topicArticleRepository.findByTopicIdWithDetails(topic.getId());

        if (articles.isEmpty()) {
            throw new IllegalStateException(
                    "Topic " + topic.getId() + " has no linked articles — cannot summarize");
        }

        String prompt   = promptBuilder.buildSummaryPrompt(articles);
        String aiText   = geminiClient.generate(prompt);
        String model    = geminiClient.getModel();

        topic.applySummary(aiText, model);

        log.info("토픽 요약 완료 topicId={} model={} chars={}", topic.getId(), model, aiText.length());
        return SummarizeResponse.from(topic);
    }
}
