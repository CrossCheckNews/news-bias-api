package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.TopicArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicArticleRepository extends JpaRepository<TopicArticle, Long> {

    // article + publisher를 한 번에 fetch해서 N+1 방지
    @Query("SELECT ta FROM TopicArticle ta JOIN FETCH ta.article a JOIN FETCH a.publisher WHERE ta.topic.id = :topicId ORDER BY ta.linkedAt DESC")
    List<TopicArticle> findByTopicIdWithDetails(@Param("topicId") Long topicId);

    boolean existsByTopicIdAndArticleId(Long topicId, Long articleId);

    void deleteByTopicIdAndArticleId(Long topicId, Long articleId);

    long countByTopicId(Long topicId);
}
