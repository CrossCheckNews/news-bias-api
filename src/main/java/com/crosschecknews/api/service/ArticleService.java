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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;

    public Page<ArticleResponse> findAll(Long publisherId, int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.desc("publishedAt"));
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
}
