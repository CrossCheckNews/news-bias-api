package com.crosschecknews.api.service;

// Design Ref: §6.2 — ArticleService: RSS fetch + manual CRUD
// Plan SC: FR-04, FR-05, FR-06, FR-07, FR-08
import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.ArticleFetchRequest;
import com.crosschecknews.api.dto.ArticleRequest;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crosschecknews.api.exception.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final PublisherService publisherService;
    private final RssFetchService rssFetchService;

    // Plan SC: FR-04, FR-05 — RSS fetch → DB save, skip duplicates
    @Transactional
    public FetchResult fetchFromRss(Long publisherId) {
        Publisher publisher = publisherService.getPublisher(publisherId);
        List<RssFetchService.RssEntry> entries = rssFetchService.fetch(publisher.getRssUrl());

        int fetched = 0;
        int skipped = 0;

        for (RssFetchService.RssEntry entry : entries) {
            if (articleRepository.existsByUrl(entry.link())) {
                skipped++;
                continue;
            }
            Article article = Article.builder()
                    .headline(entry.title())
                    .url(entry.link())
                    .publishedAt(entry.publishedAt())
                    .publisher(publisher)
                    .build();
            articleRepository.save(article);
            fetched++;
        }

        return new FetchResult(fetched, skipped);
    }

    // Plan SC: FR-08 — 수동 등록 (중복 URL은 GlobalExceptionHandler가 409로 처리)
    @Transactional
    public ArticleResponse create(ArticleRequest request) {
        Publisher publisher = publisherService.getPublisher(request.getPublisherId());
        Article article = Article.builder()
                .headline(request.getHeadline())
                .url(request.getUrl())
                .publishedAt(request.getPublishedAt())
                .publisher(publisher)
                .build();
        return ArticleResponse.from(articleRepository.save(article));
    }

    // Plan SC: FR-06 — publisherId 필터 + 페이지네이션
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

    public record FetchResult(int fetched, int skipped) {}
}
