package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.ArticleCandidate;
import com.crosschecknews.api.dto.NormalizedArticleCandidate;
import com.crosschecknews.api.repository.PublisherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArticleNormalizationServiceTest {

    @Mock
    private PublisherRepository publisherRepository;

    @InjectMocks
    private ArticleNormalizationService service;

    // ── normalizeUrl ─────────────────────────────────────────────────────────

    @Nested
    class NormalizeUrl {

        @Test
        void http를_https로_변환한다() {
            String result = service.normalizeUrl("http://example.com/news/article");
            assertThat(result).startsWith("https://");
        }

        @Test
        void trailing_slash를_제거한다() {
            String withSlash    = service.normalizeUrl("https://example.com/news/");
            String withoutSlash = service.normalizeUrl("https://example.com/news");
            assertThat(withSlash).isEqualTo(withoutSlash);
        }

        @Test
        void UTM_파라미터를_제거한다() {
            String url = "https://example.com/news?utm_source=google&utm_medium=cpc&utm_campaign=test";
            String result = service.normalizeUrl(url);
            assertThat(result).doesNotContain("utm_source", "utm_medium", "utm_campaign");
        }

        @Test
        void fbclid_gclid_파라미터를_제거한다() {
            String url = "https://example.com/news?fbclid=abc123&gclid=def456";
            String result = service.normalizeUrl(url);
            assertThat(result).doesNotContain("fbclid", "gclid");
        }

        @Test
        void 트래킹_파라미터_외_쿼리는_유지한다() {
            String url = "https://example.com/news?id=42&utm_source=google";
            String result = service.normalizeUrl(url);
            assertThat(result).contains("id=42");
            assertThat(result).doesNotContain("utm_source");
        }

        @Test
        void 결과가_소문자로_반환된다() {
            String result = service.normalizeUrl("https://EXAMPLE.COM/News/Article");
            assertThat(result).isEqualTo(result.toLowerCase());
        }

        @Test
        void null이면_null을_반환한다() {
            assertThat(service.normalizeUrl(null)).isNull();
        }

        @Test
        void 루트_경로만_있을때_슬래시_하나를_유지한다() {
            String result = service.normalizeUrl("https://example.com/");
            assertThat(result).isEqualTo("https://example.com/");
        }
    }

    // ── normalizeHeadline ────────────────────────────────────────────────────

    @Nested
    class NormalizeHeadline {

        @Test
        void 소문자로_변환한다() {
            assertThat(service.normalizeHeadline("Breaking NEWS: Big Event"))
                    .isEqualTo("breaking news big event");
        }

        @Test
        void 구두점을_공백으로_변환한다() {
            assertThat(service.normalizeHeadline("Hello, World! What's next?"))
                    .doesNotContain(",", "!", "?", "'");
        }

        @Test
        void 연속_공백을_단일_공백으로_정리한다() {
            String result = service.normalizeHeadline("Too   many   spaces");
            assertThat(result).doesNotContain("  ");
        }

        @Test
        void 앞뒤_공백을_제거한다() {
            assertThat(service.normalizeHeadline("  headline  ")).isEqualTo("headline");
        }

        @Test
        void null이면_빈문자열을_반환한다() {
            assertThat(service.normalizeHeadline(null)).isEmpty();
        }

        @Test
        void 동일_내용의_헤드라인은_같은_결과를_반환한다() {
            String a = service.normalizeHeadline("U.S. Stocks Rise!");
            String b = service.normalizeHeadline("U.S. Stocks Rise!");
            assertThat(a).isEqualTo(b);
        }
    }

    // ── generateDedupeKey ────────────────────────────────────────────────────

    @Nested
    class GenerateDedupeKey {

        @Test
        void 동일한_입력은_항상_같은_키를_생성한다() {
            ArticleCandidate candidate = candidateWithDate(LocalDateTime.of(2026, 4, 8, 12, 0));

            String key1 = service.generateDedupeKey("BBC", "breaking news", candidate);
            String key2 = service.generateDedupeKey("BBC", "breaking news", candidate);

            assertThat(key1).isEqualTo(key2);
        }

        @Test
        void publisher가_다르면_다른_키를_생성한다() {
            ArticleCandidate candidate = candidateWithDate(LocalDateTime.of(2026, 4, 8, 12, 0));

            String key1 = service.generateDedupeKey("BBC",    "breaking news", candidate);
            String key2 = service.generateDedupeKey("Reuters", "breaking news", candidate);

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void 헤드라인이_다르면_다른_키를_생성한다() {
            ArticleCandidate candidate = candidateWithDate(LocalDateTime.of(2026, 4, 8, 12, 0));

            String key1 = service.generateDedupeKey("BBC", "breaking news", candidate);
            String key2 = service.generateDedupeKey("BBC", "other news",    candidate);

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void 날짜가_다르면_다른_키를_생성한다() {
            ArticleCandidate candidate1 = candidateWithDate(LocalDateTime.of(2026, 4, 7, 12, 0));
            ArticleCandidate candidate2 = candidateWithDate(LocalDateTime.of(2026, 4, 8, 12, 0));

            String key1 = service.generateDedupeKey("BBC", "breaking news", candidate1);
            String key2 = service.generateDedupeKey("BBC", "breaking news", candidate2);

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void publishedAt이_null이면_unknown으로_처리한다() {
            ArticleCandidate candidate = candidateWithDate(null);

            String key = service.generateDedupeKey("BBC", "breaking news", candidate);

            assertThat(key).isNotNull().isNotBlank();
        }

        @Test
        void 결과는_SHA256_hex_형식이다() {
            ArticleCandidate candidate = candidateWithDate(LocalDateTime.of(2026, 4, 8, 12, 0));

            String key = service.generateDedupeKey("BBC", "breaking news", candidate);

            assertThat(key).matches("[a-f0-9]{64}");
        }

        private ArticleCandidate candidateWithDate(LocalDateTime publishedAt) {
            return ArticleCandidate.builder()
                    .publisherName("BBC")
                    .publisherCode("BBC_WORLD")
                    .headline("Breaking News")
                    .url("https://bbc.com/news/article")
                    .category(ArticleCategory.WORLD)
                    .country(Country.GB)
                    .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                    .publishedAt(publishedAt)
                    .build();
        }
    }

    // ── normalize (통합) ─────────────────────────────────────────────────────

    @Nested
    class NormalizeFull {

        private Publisher stubPublisher;

        @BeforeEach
        void setUp() {
            stubPublisher = Publisher.builder()
                    .name("BBC")
                    .country(Country.GB)
                    .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                    .build();
        }

        @Test
        void 전체_정규화_결과가_올바르게_조립된다() {
            given(publisherRepository.findByName("BBC")).willReturn(Optional.of(stubPublisher));

            ArticleCandidate candidate = ArticleCandidate.builder()
                    .publisherName("BBC")
                    .publisherCode("BBC_WORLD")
                    .headline("  Breaking NEWS!  ")
                    .url("http://bbc.com/news/?utm_source=google")
                    .description("<p>Some &amp; description</p>")
                    .rssGuid("guid-001")
                    .category(ArticleCategory.WORLD)
                    .country(Country.GB)
                    .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                    .publishedAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                    .build();

            NormalizedArticleCandidate result = service.normalize(candidate);

            assertThat(result.getHeadline()).isEqualTo("Breaking NEWS!");
            assertThat(result.getNormalizedHeadline()).isEqualTo("breaking news");
            assertThat(result.getNormalizedUrl()).startsWith("https://");
            assertThat(result.getNormalizedUrl()).doesNotContain("utm_source");
            assertThat(result.getDescription()).isEqualTo("Some & description");
            assertThat(result.getDedupeKey()).matches("[a-f0-9]{64}");
            assertThat(result.getPublisher()).isEqualTo(stubPublisher);
        }

        @Test
        void headline의_HTML_엔티티가_디코딩된다() {
            given(publisherRepository.findByName("BBC")).willReturn(Optional.of(stubPublisher));

            ArticleCandidate candidate = ArticleCandidate.builder()
                    .publisherName("BBC")
                    .publisherCode("BBC_WORLD")
                    .headline("Iran&apos;s ambassador calls Trump&apos;s post &quot;alarming&quot;")
                    .url("https://bbc.com/news/iran")
                    .description("Iran&apos;s UN ambassador is &quot;deeply irresponsible&quot; &amp; &lt;alarming&gt;")
                    .category(ArticleCategory.WORLD)
                    .country(Country.GB)
                    .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                    .publishedAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                    .build();

            NormalizedArticleCandidate result = service.normalize(candidate);

            assertThat(result.getHeadline()).isEqualTo("Iran's ambassador calls Trump's post \"alarming\"");
            assertThat(result.getDescription()).isEqualTo("Iran's UN ambassador is \"deeply irresponsible\" & <alarming>");
        }

        @Test
        void description이_null이면_null로_유지된다() {
            given(publisherRepository.findByName("BBC")).willReturn(Optional.of(stubPublisher));

            ArticleCandidate candidate = ArticleCandidate.builder()
                    .publisherName("BBC")
                    .publisherCode("BBC_WORLD")
                    .headline("Headline")
                    .url("https://bbc.com/news")
                    .description(null)
                    .category(ArticleCategory.WORLD)
                    .country(Country.GB)
                    .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                    .publishedAt(LocalDateTime.of(2026, 4, 8, 9, 0))
                    .build();

            NormalizedArticleCandidate result = service.normalize(candidate);

            assertThat(result.getDescription()).isNull();
        }
    }
}
