package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.PublisherFeed;
import com.crosschecknews.api.dto.ArticleRequest;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.PublisherFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final PublisherService publisherService;
    private final PublisherFeedRepository publisherFeedRepository;
    private final RssFetchService rssFetchService;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public Page<ArticleResponse> findAll(Long publisherId, Pageable pageable) {
        if (publisherId != null) {
            return articleRepository.findByPublisherId(publisherId, pageable)
                    .map(ArticleResponse::from);
        }
        return articleRepository.findAll(pageable).map(ArticleResponse::from);
    }

    public ArticleResponse findById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
        return ArticleResponse.from(article);
    }

    // ── 레거시 (Stage 1 구현, Stage 2 이후 ArticleSaveService로 대체) ─────────
    //
    // 아래 두 메서드는 Stage 1 개발 당시 작성된 코드로,
    // Article 엔티티의 normalizedHeadline / normalizedUrl / dedupeKey / category 필드가
    // 추가되기 전에 만들어져 현재 DB 제약 조건을 충족하지 못합니다.
    //
    // 실제 수집은 POST /api/v1/collect/fetch-and-save 또는
    //            POST /api/v1/pipeline/collect 을 사용하십시오.
    // HTTP 엔드포인트는 노출하지 않습니다.

    @Deprecated
    @Transactional
    FetchResult fetchFromRss(Long feedId) {
        PublisherFeed feed = publisherFeedRepository.findById(feedId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + feedId));
        List<RssFetchService.RssEntry> entries = rssFetchService.fetch(feed.getRssUrl());

        int fetched = 0, skipped = 0;
        for (RssFetchService.RssEntry entry : entries) {
            if (articleRepository.existsByUrl(entry.link())) {
                skipped++;
                continue;
            }
            Article article = Article.builder()
                    .headline(entry.title())
                    .url(entry.link())
                    .publishedAt(entry.publishedAt())
                    .publisher(feed.getPublisher())
                    .build();
            articleRepository.save(article);
            fetched++;
        }
        return new FetchResult(fetched, skipped);
    }

    @Deprecated
    @Transactional
    ArticleResponse create(ArticleRequest request) {
        var publisher = publisherService.getPublisher(request.getPublisherId());
        Article article = Article.builder()
                .headline(request.getHeadline())
                .url(request.getUrl())
                .publishedAt(request.getPublishedAt())
                .publisher(publisher)
                .build();
        return ArticleResponse.from(articleRepository.save(article));
    }

    /** @deprecated use {@link ArticleSaveService} */
    @Deprecated
    record FetchResult(int fetched, int skipped) {}
}
