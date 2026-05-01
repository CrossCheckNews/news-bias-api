package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.TopicDetailResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * TopicService.findById() — TopicDetailResponse 반환 검증.
 *
 * 검증 항목:
 * - Topic 도메인 필드 (category, createdAt, aiModel, summaryGeneratedAt)
 * - Article 도메인 필드 (url, publishedAt, description)
 * - Publisher 도메인 필드 (country, politicalLeaning)
 * - 분포 집계 (leaningDistribution, countryDistribution)
 * - 존재하지 않는 Topic 예외 처리
 */
@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock TopicRepository        topicRepository;
    @Mock TopicArticleRepository topicArticleRepository;

    @InjectMocks TopicService topicService;

    // ── 픽스처 ────────────────────────────────────────────────────────────────

    private static final LocalDateTime CREATED_AT       = LocalDateTime.of(2026, 4, 9, 12, 0);
    private static final LocalDateTime SUMMARY_AT       = LocalDateTime.of(2026, 4, 9, 13, 0);
    private static final LocalDateTime PUBLISHED_AT_FOX = LocalDateTime.of(2026, 4, 9, 10, 0);
    private static final LocalDateTime PUBLISHED_AT_NYT = LocalDateTime.of(2026, 4, 9, 11, 0);

    private Topic buildTopic(boolean withSummary) {
        Topic.TopicBuilder builder = Topic.builder()
                .id(1L)
                .title("Trump tariff policy sparks global trade dispute")
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 4, 9))
                .createdAt(CREATED_AT);

        if (withSummary) {
            builder.aiSummary("트럼프 대통령이 주요 교역국에 상호 관세를 부과하겠다고 발표하면서 국제 무역 갈등이 고조되고 있다.")
                   .aiModel("gemini-2.0-flash")
                   .summaryGeneratedAt(SUMMARY_AT);
        }
        return builder.build();
    }

    private Publisher buildPublisher(long id, String name, Country country, PoliticalLeaning leaning) {
        return Publisher.builder()
                .id(id).name(name)
                .country(country)
                .politicalLeaning(leaning)
                .build();
    }

    private Article buildArticle(long id, Publisher publisher, String headline,
                                  String url, LocalDateTime publishedAt, String description) {
        return Article.builder()
                .id(id)
                .headline(headline)
                .normalizedHeadline(headline.toLowerCase())
                .url(url)
                .normalizedUrl(url)
                .description(description)
                .publishedAt(publishedAt)
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .dedupeKey("key-" + id)
                .build();
    }

    private TopicArticle buildTopicArticle(long id, Topic topic, Article article) {
        return TopicArticle.builder()
                .id(id).topic(topic).article(article)
                .build();
    }

    // ── Topic 도메인 필드 검증 ─────────────────────────────────────────────────

    @Test
    void 토픽_기본_도메인_필드가_모두_포함된다() {
        Topic topic = buildTopic(false);
        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of());

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getId()).isEqualTo(1L);
        assertThat(res.getTitle()).isEqualTo("Trump tariff policy sparks global trade dispute");
        assertThat(res.getArticleCategory()).isEqualTo(ArticleCategory.WORLD);
        assertThat(res.getStatus()).isEqualTo(TopicStatus.ACTIVE);
        assertThat(res.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 9));
        assertThat(res.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void AI_요약이_있으면_aiSummary_aiModel_summaryGeneratedAt이_포함된다() {
        Topic topic = buildTopic(true);
        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of());

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getAiSummary()).isEqualTo(
                "트럼프 대통령이 주요 교역국에 상호 관세를 부과하겠다고 발표하면서 국제 무역 갈등이 고조되고 있다.");
        assertThat(res.getAiModel()).isEqualTo("gemini-2.0-flash");
        assertThat(res.getSummaryGeneratedAt()).isEqualTo(SUMMARY_AT);
    }

    @Test
    void AI_요약이_없으면_aiSummary_관련_필드가_null이다() {
        Topic topic = buildTopic(false);
        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of());

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getAiSummary()).isNull();
        assertThat(res.getAiModel()).isNull();
        assertThat(res.getSummaryGeneratedAt()).isNull();
    }

    // ── Article + Publisher 도메인 필드 검증 ──────────────────────────────────

    @Test
    void 기사_목록에_url_publishedAt_description이_포함된다() {
        Topic topic = buildTopic(false);
        Publisher fox = buildPublisher(1L, "Fox News", Country.US, PoliticalLeaning.CONSERVATIVE);
        Article article = buildArticle(
                10L, fox,
                "Trump announces reciprocal tariffs on all trading partners",
                "https://www.foxnews.com/politics/trump-tariff-1234",
                PUBLISHED_AT_FOX,
                "President Trump announced sweeping tariffs targeting major trade partners."
        );
        TopicArticle ta = buildTopicArticle(1L, topic, article);

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(ta));

        TopicDetailResponse res = topicService.findById(1L);
        TopicDetailResponse.ArticleEntry entry = res.getArticles().get(0);

        assertThat(entry.getArticleId()).isEqualTo(10L);
        assertThat(entry.getHeadline()).isEqualTo("Trump announces reciprocal tariffs on all trading partners");
        assertThat(entry.getUrl()).isEqualTo("https://www.foxnews.com/politics/trump-tariff-1234");
        assertThat(entry.getPublishedAt()).isEqualTo(PUBLISHED_AT_FOX);
        assertThat(entry.getDescription()).isEqualTo(
                "President Trump announced sweeping tariffs targeting major trade partners.");
    }

    @Test
    void 기사_목록에_publisher_country_politicalLeaning이_포함된다() {
        Topic topic = buildTopic(false);
        Publisher hani = buildPublisher(2L, "Hankyoreh", Country.KR, PoliticalLeaning.PROGRESSIVE);
        Article article = buildArticle(
                20L, hani,
                "트럼프 관세 폭탄, 한국 수출 기업 직격탄",
                "https://www.hani.co.kr/arti/international/2026/abc",
                PUBLISHED_AT_NYT,
                "트럼프 대통령의 상호관세 발표에 한국 수출 기업들이 큰 타격을 받을 것으로 예상된다."
        );
        TopicArticle ta = buildTopicArticle(2L, topic, article);

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(ta));

        TopicDetailResponse res = topicService.findById(1L);
        TopicDetailResponse.ArticleEntry entry = res.getArticles().get(0);

        assertThat(entry.getPublisherName()).isEqualTo("Hankyoreh");
        assertThat(entry.getCountry()).isEqualTo("KR");
        assertThat(entry.getPoliticalLeaning()).isEqualTo("PROGRESSIVE");
    }

    @Test
    void description이_없는_기사는_null로_반환된다() {
        Topic topic = buildTopic(false);
        Publisher fox = buildPublisher(1L, "Fox News", Country.US, PoliticalLeaning.CONSERVATIVE);
        Article article = buildArticle(
                10L, fox,
                "Trump tariff news",
                "https://www.foxnews.com/1",
                PUBLISHED_AT_FOX,
                null   // description 없음
        );

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L))
                .willReturn(List.of(buildTopicArticle(1L, topic, article)));

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getArticles().get(0).getDescription()).isNull();
    }

    // ── articleCount 검증 ────────────────────────────────────────────────────

    @Test
    void articleCount는_연결된_기사_수와_일치한다() {
        Topic topic = buildTopic(false);
        Publisher fox = buildPublisher(1L, "Fox News", Country.US, PoliticalLeaning.CONSERVATIVE);
        Publisher nyt = buildPublisher(2L, "New York Times", Country.US, PoliticalLeaning.PROGRESSIVE);
        Article a1 = buildArticle(1L, fox, "headline 1", "https://fox.com/1", PUBLISHED_AT_FOX, null);
        Article a2 = buildArticle(2L, nyt, "headline 2", "https://nyt.com/1", PUBLISHED_AT_NYT, null);

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(
                buildTopicArticle(1L, topic, a1),
                buildTopicArticle(2L, topic, a2)
        ));

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getArticleCount()).isEqualTo(2);
        assertThat(res.getArticles()).hasSize(2);
    }

    // ── 분포 집계 검증 ────────────────────────────────────────────────────────

    @Test
    void leaningDistribution은_정치성향별_기사수를_집계한다() {
        Topic topic = buildTopic(false);
        Publisher fox     = buildPublisher(1L, "Fox News",       Country.US, PoliticalLeaning.CONSERVATIVE);
        Publisher chosun  = buildPublisher(2L, "Chosun Ilbo",    Country.KR, PoliticalLeaning.CONSERVATIVE);
        Publisher nyt     = buildPublisher(3L, "New York Times", Country.US, PoliticalLeaning.PROGRESSIVE);
        Article a1 = buildArticle(1L, fox,    "h1", "https://fox.com/1",    PUBLISHED_AT_FOX, null);
        Article a2 = buildArticle(2L, chosun, "h2", "https://chosun.com/1", PUBLISHED_AT_FOX, null);
        Article a3 = buildArticle(3L, nyt,    "h3", "https://nyt.com/1",    PUBLISHED_AT_NYT, null);

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(
                buildTopicArticle(1L, topic, a1),
                buildTopicArticle(2L, topic, a2),
                buildTopicArticle(3L, topic, a3)
        ));

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getLeaningDistribution()).containsEntry("CONSERVATIVE", 2);
        assertThat(res.getLeaningDistribution()).containsEntry("PROGRESSIVE", 1);
    }

    @Test
    void countryDistribution은_국가별_기사수를_집계한다() {
        Topic topic = buildTopic(false);
        Publisher fox  = buildPublisher(1L, "Fox News",    Country.US, PoliticalLeaning.CONSERVATIVE);
        Publisher nyt  = buildPublisher(2L, "New York Times", Country.US, PoliticalLeaning.PROGRESSIVE);
        Publisher hani = buildPublisher(3L, "Hankyoreh",   Country.KR, PoliticalLeaning.PROGRESSIVE);
        Article a1 = buildArticle(1L, fox,  "h1", "https://fox.com/1",  PUBLISHED_AT_FOX, null);
        Article a2 = buildArticle(2L, nyt,  "h2", "https://nyt.com/1",  PUBLISHED_AT_NYT, null);
        Article a3 = buildArticle(3L, hani, "h3", "https://hani.co.kr/1", PUBLISHED_AT_NYT, null);

        given(topicRepository.findById(1L)).willReturn(Optional.of(topic));
        given(topicArticleRepository.findByTopicIdWithDetails(1L)).willReturn(List.of(
                buildTopicArticle(1L, topic, a1),
                buildTopicArticle(2L, topic, a2),
                buildTopicArticle(3L, topic, a3)
        ));

        TopicDetailResponse res = topicService.findById(1L);

        assertThat(res.getCountryDistribution()).containsEntry("US", 2);
        assertThat(res.getCountryDistribution()).containsEntry("KR", 1);
    }

    // ── 예외 처리 ─────────────────────────────────────────────────────────────

    @Test
    void 존재하지_않는_토픽_조회시_ResourceNotFoundException을_던진다() {
        given(topicRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
