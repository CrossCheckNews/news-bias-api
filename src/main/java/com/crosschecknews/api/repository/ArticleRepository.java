package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.ArticleCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByFetchedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<Article> findByPublishedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<Article> findByHeadlineContainingIgnoreCase(String headline, Pageable pageable);

    Page<Article> findByHeadlineContainingIgnoreCaseAndPublishedAtBetween(
            String headline, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Priority 1 — rssGuid는 publisher 내에서만 고유
    boolean existsByPublisher_IdAndRssGuid(Long publisherId, String rssGuid);

    // Priority 2 — normalizedUrl은 전역 고유
    boolean existsByNormalizedUrl(String normalizedUrl);

    // Priority 3 — 최후 fallback (publisher + 제목 + 날짜 해시)
    boolean existsByDedupeKey(String dedupeKey);

    long countByFetchedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT a.publisher.name, COUNT(a)
            FROM Article a
            GROUP BY a.publisher.name
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> countArticlesByPublisher();

    // 클러스터링 후보: 특정 articleCategory + 시간 범위 + ACTIVE Topic에 미연결
    @Query("""
            SELECT a FROM Article a
            JOIN FETCH a.publisher
            WHERE a.category = :category
              AND a.fetchedAt >= :since
              AND NOT EXISTS (
                  SELECT ta FROM TopicArticle ta
                  JOIN ta.topic t
                  WHERE ta.article = a
                    AND t.status = 'ACTIVE'
              )
            ORDER BY a.fetchedAt DESC
            """)
    List<Article> findClusteringCandidates(
            @Param("category") ArticleCategory category,
            @Param("since") LocalDateTime since
    );
}
