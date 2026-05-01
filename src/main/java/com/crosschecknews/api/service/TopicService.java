package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import com.crosschecknews.api.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final ArticleRepository articleRepository;

    @Transactional
    public TopicResponse create(TopicRequest request) {
        Topic topic = Topic.builder()
                .title(request.getTitle())
                .summary(request.getSummary())
                .articleCategory(request.getArticleCategory())
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .build();
        Topic saved = topicRepository.save(topic);
        return TopicResponse.empty(saved);
    }

    public Page<TopicResponse> findAll(TopicStatus status, LocalDate date, int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.desc("createdAt"));
        Page<Topic> topics;
        if (date != null) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();
            topics = (status != null)
                    ? topicRepository.findByStatusAndCreatedAtBetween(status, from, to, pageable)
                    : topicRepository.findByCreatedAtBetween(from, to, pageable);
        } else {
            topics = (status != null)
                    ? topicRepository.findByStatus(status, pageable)
                    : topicRepository.findAll(pageable);
        }

        // 페이지 내 전체 topicId를 한 번에 조회해 N+1 방지
        List<Long> topicIds = topics.stream().map(Topic::getId).toList();
        Map<Long, List<TopicArticle>> linksByTopic = topicArticleRepository
                .findByTopicIdInWithDetails(topicIds)
                .stream()
                .collect(Collectors.groupingBy(ta -> ta.getTopic().getId()));

        return topics.map(topic ->
                TopicResponse.from(topic, linksByTopic.getOrDefault(topic.getId(), List.of()))
        );
    }

    public TopicDetailResponse findById(Long topicId) {
        Topic topic = getTopic(topicId);
        List<TopicArticle> links = topicArticleRepository.findByTopicIdWithDetails(topicId);
        return TopicDetailResponse.from(topic, links);
    }

    @Transactional
    public TopicResponse update(Long topicId, TopicRequest request) {
        Topic topic = getTopic(topicId);
        topic.update(request.getTitle(), request.getSummary(), request.getStatus(), request.getStartDate());
        List<TopicArticle> links = topicArticleRepository.findByTopicIdWithDetails(topicId);
        return TopicResponse.from(topic, links);
    }

    @Transactional
    public void delete(Long topicId) {
        topicRepository.delete(getTopic(topicId));
    }

    @Transactional
    public void linkArticle(Long topicId, Long articleId) {
        Topic topic = getTopic(topicId);
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + articleId));

        // 중복 연결은 DB unique constraint + GlobalExceptionHandler(409)가 처리
        TopicArticle link = TopicArticle.builder()
                .topic(topic)
                .article(article)
                .build();
        topicArticleRepository.save(link);
    }

    @Transactional
    public void unlinkArticle(Long topicId, Long articleId) {
        getTopic(topicId);
        if (!topicArticleRepository.existsByTopicIdAndArticleId(topicId, articleId)) {
            throw new ResourceNotFoundException(
                    "Link not found: topicId=" + topicId + ", articleId=" + articleId);
        }
        topicArticleRepository.deleteByTopicIdAndArticleId(topicId, articleId);
    }

    // 비교 뷰: groupBy=leaning(default) or country (null = flat)
    public TopicComparisonResponse getComparisonView(Long topicId, String groupBy) {
        Topic topic = getTopic(topicId);
        List<TopicArticle> links = topicArticleRepository.findByTopicIdWithDetails(topicId);
        return TopicComparisonResponse.of(topic, links, groupBy);
    }

    Topic getTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));
    }
}
