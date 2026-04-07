package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ArticleRequest;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.PublisherFeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private PublisherService publisherService;
    @Mock private PublisherFeedRepository publisherFeedRepository;
    @Mock private RssFetchService rssFetchService;

    @InjectMocks
    private ArticleService articleService;

    private Publisher buildPublisher() {
        return Publisher.builder()
                .id(1L).name("Fox News")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    private PublisherFeed buildFeed(Publisher publisher) {
        return PublisherFeed.builder()
                .id(1L).publisher(publisher)
                .category(Category.WORLD)
                .rssUrl("https://moxie.foxnews.com/google-publisher/world.xml")
                .build();
    }

    private Article buildArticle(Publisher publisher) {
        return Article.builder()
                .id(1L).headline("Test Headline")
                .url("https://foxnews.com/1")
                .publishedAt(LocalDateTime.now())
                .publisher(publisher)
                .build();
    }

    @Test
    void RSS_수집_성공() {
        Publisher publisher = buildPublisher();
        PublisherFeed feed = buildFeed(publisher);
        List<RssFetchService.RssEntry> entries = List.of(
                new RssFetchService.RssEntry("Headline 1", "https://foxnews.com/1", null, "guid-1", LocalDateTime.now()),
                new RssFetchService.RssEntry("Headline 2", "https://foxnews.com/2", null, "guid-2", LocalDateTime.now())
        );
        given(publisherFeedRepository.findById(1L)).willReturn(Optional.of(feed));
        given(rssFetchService.fetch(feed.getRssUrl())).willReturn(entries);
        given(articleRepository.existsByUrl(any())).willReturn(false);
        given(articleRepository.save(any(Article.class))).willAnswer(inv -> inv.getArgument(0));

        ArticleService.FetchResult result = articleService.fetchFromRss(1L);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
    }

    @Test
    void 존재하지_않는_Feed_수집_실패() {
        given(publisherFeedRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.fetchFromRss(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void 중복_URL_skip() {
        Publisher publisher = buildPublisher();
        PublisherFeed feed = buildFeed(publisher);
        List<RssFetchService.RssEntry> entries = List.of(
                new RssFetchService.RssEntry("Headline 1", "https://foxnews.com/1", null, "guid-1", LocalDateTime.now()),
                new RssFetchService.RssEntry("Headline 2", "https://foxnews.com/2", null, "guid-2", LocalDateTime.now())
        );
        given(publisherFeedRepository.findById(1L)).willReturn(Optional.of(feed));
        given(rssFetchService.fetch(feed.getRssUrl())).willReturn(entries);
        given(articleRepository.existsByUrl("https://foxnews.com/1")).willReturn(true);
        given(articleRepository.existsByUrl("https://foxnews.com/2")).willReturn(false);
        given(articleRepository.save(any(Article.class))).willAnswer(inv -> inv.getArgument(0));

        ArticleService.FetchResult result = articleService.fetchFromRss(1L);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void 기사_수동_등록_성공() {
        Publisher publisher = buildPublisher();
        Article saved = buildArticle(publisher);
        ArticleRequest request = new ArticleRequest("Test Headline", "https://foxnews.com/1",
                LocalDateTime.now(), 1L);

        given(publisherService.getPublisher(1L)).willReturn(publisher);
        given(articleRepository.save(any(Article.class))).willReturn(saved);

        ArticleResponse response = articleService.create(request);

        assertThat(response.getHeadline()).isEqualTo("Test Headline");
        assertThat(response.getPublisherName()).isEqualTo("Fox News");
    }

    @Test
    void 기사_단건_조회_성공() {
        Publisher publisher = buildPublisher();
        given(articleRepository.findById(1L)).willReturn(Optional.of(buildArticle(publisher)));

        ArticleResponse response = articleService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_기사_조회_실패() {
        given(articleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
