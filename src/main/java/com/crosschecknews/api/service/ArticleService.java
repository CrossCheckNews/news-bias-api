package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;

    public Page<ArticleResponse> findAll(String headline, LocalDate date, int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.desc("publishedAt"));
        boolean hasHeadline = headline != null && !headline.isBlank();
        boolean hasDate = date != null;

        if (hasHeadline && hasDate) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = from.plusDays(1);
            return articleRepository.findByHeadlineContainingIgnoreCaseAndPublishedAtBetween(headline, from, to, pageable)
                    .map(ArticleResponse::from);
        }
        if (hasHeadline) {
            return articleRepository.findByHeadlineContainingIgnoreCase(headline, pageable)
                    .map(ArticleResponse::from);
        }
        if (hasDate) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = from.plusDays(1);
            return articleRepository.findByPublishedAtBetween(from, to, pageable)
                    .map(ArticleResponse::from);
        }
        return articleRepository.findAll(pageable).map(ArticleResponse::from);
    }

    public ArticleResponse findById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
        return ArticleResponse.from(article);
    }
}
