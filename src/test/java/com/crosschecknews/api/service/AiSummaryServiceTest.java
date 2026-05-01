package com.crosschecknews.api.service;

import com.crosschecknews.api.client.FallbackAiClient;
import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.SummarizeResponse;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Stage 5: AI 요약 생성 테스트.
 *
 * 핵심 로직:
 * - aiSummary가 없는 ACTIVE Topic 일괄 처리
 * - GeminiClient → 요약 텍스트 생성
 * - Topic.applySummary() 호출 → aiSummary 필드 업데이트
 * - 개별 토픽 실패 시 로그만 남기고 계속 진행
 */
@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock TopicRepository        topicRepository;
    @Mock TopicArticleRepository topicArticleRepository;
    @Mock FallbackAiClient       geminiClient;
    @Mock PromptBuilder          promptBuilder;

    @InjectMocks AiSummaryService aiSummaryService;

    private Topic topic(long id) {
        return Topic.builder()
                .id(id).title("Topic " + id)
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ACTIVE)
                .startDate(LocalDate.now())
                .build();
    }

    private TopicArticle topicArticle(Topic topic, String publisherName, String headline) {
        Publisher publisher = Publisher.builder()
                .id(1L).name(publisherName)
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
        Article article = Article.builder()
                .id(1L).headline(headline)
                .normalizedHeadline(headline.toLowerCase())
                .url("https://example.com/1")
                .normalizedUrl("https://example.com/1")
                .dedupeKey("key-1")
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build();
        return TopicArticle.builder()
                .id(1L).topic(topic).article(article)
                .build();
    }

    // ── 일괄 요약 ─────────────────────────────────────────────────────────────

    @Test
    void 대기_Topic_일괄_요약_성공() {
        Topic t1 = topic(1L);
        Topic t2 = topic(2L);
        TopicArticle ta1 = topicArticle(t1, "Fox News", "Trump wins election");
        TopicArticle ta2 = topicArticle(t2, "NYT", "Japan earthquake strikes");

        given(topicRepository.findByAiSummaryIsNullAndStatus(TopicStatus.ACTIVE))
                .willReturn(List.of(t1, t2));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(ta1));
        given(topicArticleRepository.findByTopicIdWithDetails(2L)).willReturn(List.of(ta2));
        given(promptBuilder.buildSummaryPrompt(any())).willReturn("prompt");
        given(geminiClient.generate(any())).willReturn("요약 텍스트");
        given(geminiClient.getModel()).willReturn("gemini-2.0-flash");

        List<SummarizeResponse> results = aiSummaryService.summarizeAll();

        assertThat(results).hasSize(2);
        verify(geminiClient, times(2)).generate(any());
    }

    @Test
    void 대기_Topic이_없으면_빈_리스트를_반환한다() {
        given(topicRepository.findByAiSummaryIsNullAndStatus(TopicStatus.ACTIVE))
                .willReturn(List.of());

        List<SummarizeResponse> results = aiSummaryService.summarizeAll();

        assertThat(results).isEmpty();
        verifyNoInteractions(geminiClient);
    }

    @Test
    void 일괄_요약_중_하나가_실패해도_나머지_Topic은_계속_처리된다() {
        Topic t1 = topic(1L);
        Topic t2 = topic(2L);
        TopicArticle ta2 = topicArticle(t2, "NYT", "Japan earthquake");

        given(topicRepository.findByAiSummaryIsNullAndStatus(TopicStatus.ACTIVE))
                .willReturn(List.of(t1, t2));
        // t1은 기사 없어서 실패
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of());
        // t2는 정상
        given(topicArticleRepository.findByTopicIdWithDetails(2L)).willReturn(List.of(ta2));
        given(promptBuilder.buildSummaryPrompt(any())).willReturn("prompt");
        given(geminiClient.generate(any())).willReturn("요약");
        given(geminiClient.getModel()).willReturn("gemini-2.0-flash");

        List<SummarizeResponse> results = aiSummaryService.summarizeAll();

        // t1 실패, t2 성공 → 결과 1건
        assertThat(results).hasSize(1);
        verify(geminiClient, times(1)).generate(any());
    }

    @Test
    void Gemini_API_호출_실패해도_나머지_Topic은_계속_처리된다() {
        Topic t1 = topic(1L);
        Topic t2 = topic(2L);
        TopicArticle ta1 = topicArticle(t1, "Fox News", "Trump wins");
        TopicArticle ta2 = topicArticle(t2, "NYT", "Japan earthquake");

        given(topicRepository.findByAiSummaryIsNullAndStatus(TopicStatus.ACTIVE))
                .willReturn(List.of(t1, t2));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(ta1));
        given(topicArticleRepository.findByTopicIdWithDetails(2L)).willReturn(List.of(ta2));
        given(promptBuilder.buildSummaryPrompt(any())).willReturn("prompt");
        // t1은 Gemini 오류
        given(geminiClient.generate(any()))
                .willThrow(new RuntimeException("Gemini API timeout"))
                .willReturn("t2 요약");
        given(geminiClient.getModel()).willReturn("gemini-2.0-flash");

        List<SummarizeResponse> results = aiSummaryService.summarizeAll();

        assertThat(results).hasSize(1);
        assertThat(t2.getAiSummary()).isEqualTo("t2 요약");
        assertThat(t1.getAiSummary()).isNull(); // 실패한 t1은 요약 없음
    }
}
