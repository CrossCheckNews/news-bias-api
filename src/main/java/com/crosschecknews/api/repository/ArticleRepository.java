package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByPublisherId(Long publisherId, Pageable pageable);
    boolean existsByUrl(String url);
}
