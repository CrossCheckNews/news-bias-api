package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.PublisherFeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublisherFeedRepository extends JpaRepository<PublisherFeed, Long> {
    List<PublisherFeed> findAllByActiveTrue();
    boolean existsByPublisherIdAndCategory(Long publisherId, ArticleCategory articleCategory);
}
