package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ArticleCandidate;
import com.crosschecknews.api.dto.FeedCollectResult;
import com.crosschecknews.api.dto.FetchAndSaveResult;
import com.crosschecknews.api.dto.NormalizedArticleCandidate;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.service.ArticleDeduplicationService.DuplicateCheckResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Stage 1~3: RSS 수집 → 정규화 → 저장 오케스트레이션 테스트
 */
@ExtendWith(MockitoExtension.class)
class ArticleSaveServiceTest {

    @Mock RssCollectService       rssCollectService;
    @Mock ArticleNormalizationService normalizationService;
    @Mock ArticleDeduplicationService deduplicationService;
    @Mock ArticleRepository       articleRepository;

    @InjectMocks ArticleSaveService articleSaveService;

    private Publisher publisher(long id, String name) {
        return Publisher.builder()
                .id(id).name(name)
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    private ArticleCandidate candidate(String headline, String url) {
        return ArticleCandidate.builder()
                .publisherCode("FOX_WORLD")
                .publisherName("Fox News")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .articleCategory(ArticleCategory.WORLD)
                .headline(headline)
                .url(url)
                .rssGuid("guid-" + url.hashCode())
                .publishedAt(LocalDateTime.now())
                .build();
    }

    private NormalizedArticleCandidate normalized(Publisher pub, String headline, String url) {
        return NormalizedArticleCandidate.builder()
                .headline(headline)
                .normalizedHeadline(headline.toLowerCase())
                .url(url)
                .normalizedUrl(url.toLowerCase())
                .rssGuid("guid-" + url.hashCode())
                .dedupeKey("key-" + url.hashCode())
                .publishedAt(LocalDateTime.now())
                .publisher(pub)
                .articleCategory(ArticleCategory.WORLD)
                .build();
    }

    // ── 수집/저장 성공 ───────────────────────────────────────────────────────

    @Test
    void 중복_없는_기사는_모두_저장된다() {
        Publisher pub = publisher(1L, "Fox News");
        ArticleCandidate c1 = candidate("Headline A", "https://fox.com/a");
        ArticleCandidate c2 = candidate("Headline B", "https://fox.com/b");

        FeedCollectResult feedResult = FeedCollectResult.builder()
                .feedSourceCode("FOX_WORLD").publisherName("Fox News")
                .success(true).count(2)
                .articles(List.of(c1, c2))
                .fetchedAt(LocalDateTime.now()).build();

        given(rssCollectService.collectAll()).willReturn(List.of(feedResult));
        given(normalizationService.normalize(c1)).willReturn(normalized(pub, "Headline A", "https://fox.com/a"));
        given(normalizationService.normalize(c2)).willReturn(normalized(pub, "Headline B", "https://fox.com/b"));
        given(deduplicationService.check(any())).willReturn(DuplicateCheckResult.notDuplicate());
        given(articleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FetchAndSaveResult result = articleSaveService.fetchAndSave(null);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(2);
        assertThat(result.getDuplicateCount()).isEqualTo(0);
        verify(articleRepository, times(2)).save(any());
    }

    // ── 중복 처리 ────────────────────────────────────────────────────────────

    @Test
    void 중복_기사는_저장하지_않고_count만_증가한다() {
        Publisher pub = publisher(1L, "Fox News");
        ArticleCandidate c1 = candidate("Headline A", "https://fox.com/a");
        ArticleCandidate c2 = candidate("Headline B", "https://fox.com/b");

        FeedCollectResult feedResult = FeedCollectResult.builder()
                .feedSourceCode("FOX_WORLD").publisherName("Fox News")
                .success(true).count(2)
                .articles(List.of(c1, c2))
                .fetchedAt(LocalDateTime.now()).build();

        given(rssCollectService.collectAll()).willReturn(List.of(feedResult));
        given(normalizationService.normalize(c1)).willReturn(normalized(pub, "Headline A", "https://fox.com/a"));
        given(normalizationService.normalize(c2)).willReturn(normalized(pub, "Headline B", "https://fox.com/b"));
        // c1은 중복, c2는 신규
        given(deduplicationService.check(any()))
                .willReturn(DuplicateCheckResult.duplicate(ArticleDeduplicationService.DuplicateReason.RSS_GUID))
                .willReturn(DuplicateCheckResult.notDuplicate());
        given(articleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FetchAndSaveResult result = articleSaveService.fetchAndSave(null);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        verify(articleRepository, times(1)).save(any());
    }

    @Test
    void 모든_기사가_중복이면_저장_호출이_없다() {
        Publisher pub = publisher(1L, "Fox News");
        ArticleCandidate c1 = candidate("Headline A", "https://fox.com/a");

        FeedCollectResult feedResult = FeedCollectResult.builder()
                .feedSourceCode("FOX_WORLD").publisherName("Fox News")
                .success(true).count(1).articles(List.of(c1))
                .fetchedAt(LocalDateTime.now()).build();

        given(rssCollectService.collectAll()).willReturn(List.of(feedResult));
        given(normalizationService.normalize(c1)).willReturn(normalized(pub, "Headline A", "https://fox.com/a"));
        given(deduplicationService.check(any()))
                .willReturn(DuplicateCheckResult.duplicate(ArticleDeduplicationService.DuplicateReason.NORMALIZED_URL));

        FetchAndSaveResult result = articleSaveService.fetchAndSave(null);

        assertThat(result.getSavedCount()).isEqualTo(0);
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        verify(articleRepository, never()).save(any());
    }

    // ── 실패 격리 ────────────────────────────────────────────────────────────

    @Test
    void RSS_수집_실패_피드는_건너뛰고_나머지_피드는_계속_처리된다() {
        Publisher pub = publisher(2L, "New York Times");
        ArticleCandidate nytCandidate = candidate("NYT Headline", "https://nyt.com/a");

        FeedCollectResult failedFeed = FeedCollectResult.builder()
                .feedSourceCode("FOX_WORLD").publisherName("Fox News")
                .success(false).count(0).articles(List.of())
                .errorMessage("Connection timeout")
                .fetchedAt(LocalDateTime.now()).build();

        FeedCollectResult successFeed = FeedCollectResult.builder()
                .feedSourceCode("NYT_WORLD").publisherName("New York Times")
                .success(true).count(1).articles(List.of(nytCandidate))
                .fetchedAt(LocalDateTime.now()).build();

        given(rssCollectService.collectAll()).willReturn(List.of(failedFeed, successFeed));
        given(normalizationService.normalize(nytCandidate))
                .willReturn(normalized(pub, "NYT Headline", "https://nyt.com/a"));
        given(deduplicationService.check(any())).willReturn(DuplicateCheckResult.notDuplicate());
        given(articleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FetchAndSaveResult result = articleSaveService.fetchAndSave(null);

        // NYT 피드의 기사는 저장됨
        assertThat(result.getSavedCount()).isEqualTo(1);
        // 실패한 Fox 피드가 failedSources에 포함됨
        assertThat(result.getFeeds()).hasSize(2);
        assertThat(result.getFeeds())
                .filteredOn(f -> !f.isCollectSuccess())
                .extracting(FetchAndSaveResult.FeedSummary::getFeedSourceCode)
                .containsExactly("FOX_WORLD");
    }

    @Test
    void 정규화_예외_발생시_해당_기사만_실패_처리되고_다음_기사는_저장된다() {
        Publisher pub = publisher(1L, "Fox News");
        ArticleCandidate c1 = candidate("Bad Article", "https://fox.com/bad");
        ArticleCandidate c2 = candidate("Good Article", "https://fox.com/good");

        FeedCollectResult feedResult = FeedCollectResult.builder()
                .feedSourceCode("FOX_WORLD").publisherName("Fox News")
                .success(true).count(2).articles(List.of(c1, c2))
                .fetchedAt(LocalDateTime.now()).build();

        given(rssCollectService.collectAll()).willReturn(List.of(feedResult));
        given(normalizationService.normalize(c1)).willThrow(new RuntimeException("publisher not found"));
        given(normalizationService.normalize(c2)).willReturn(normalized(pub, "Good Article", "https://fox.com/good"));
        given(deduplicationService.check(any())).willReturn(DuplicateCheckResult.notDuplicate());
        given(articleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        FetchAndSaveResult result = articleSaveService.fetchAndSave(null);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(1);
        verify(articleRepository, times(1)).save(any());
    }
}
