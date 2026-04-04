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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .build();
        Topic saved = topicRepository.save(topic);
        return TopicResponse.from(saved, 0);
    }

    public Page<TopicResponse> findAll(TopicStatus status, Pageable pageable) {
        Page<Topic> topics = (status != null)
                ? topicRepository.findByStatus(status, pageable)
                : topicRepository.findAll(pageable);
        return topics.map(topic ->
                TopicResponse.from(topic, topicArticleRepository.countByTopicId(topic.getId()))
        );
    }

    public TopicDetailResponse findById(Long topicId) {
        Topic topic = getTopic(topicId);
        List<TopicArticle> links = topicArticleRepository.findByTopicIdWithDetails(topicId);

        Map<String, Long> leaningDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getPoliticalLeaning().name(),
                        Collectors.counting()
                ));

        Map<String, Long> countryDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getCountry().name(),
                        Collectors.counting()
                ));

        return TopicDetailResponse.from(topic, links.size(), leaningDistribution, countryDistribution);
    }

    @Transactional
    public TopicResponse update(Long topicId, TopicRequest request) {
        Topic topic = getTopic(topicId);
        topic.update(request.getTitle(), request.getSummary(), request.getStatus(), request.getStartDate());
        long articleCount = topicArticleRepository.countByTopicId(topicId);
        return TopicResponse.from(topic, articleCount);
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

    // 비교 뷰: groupBy=leaning(default) or country
    public TopicComparisonResponse getComparisonView(Long topicId, String groupBy) {
        getTopic(topicId);
        List<TopicArticle> links = topicArticleRepository.findByTopicIdWithDetails(topicId);

        Map<String, List<ArticleResponse>> grouped;

        if ("country".equalsIgnoreCase(groupBy)) {
            grouped = links.stream()
                    .collect(Collectors.groupingBy(
                            ta -> ta.getArticle().getPublisher().getCountry().name(),
                            LinkedHashMap::new,
                            Collectors.mapping(ta -> ArticleResponse.from(ta.getArticle()), Collectors.toList())
                    ));
        } else {
            // leaning 기준: LEFT → CENTER → RIGHT 순서 보장
            grouped = links.stream()
                    .sorted(Comparator.comparingInt(
                            ta -> ta.getArticle().getPublisher().getPoliticalLeaning().ordinal()))
                    .collect(Collectors.groupingBy(
                            ta -> ta.getArticle().getPublisher().getPoliticalLeaning().name(),
                            LinkedHashMap::new,
                            Collectors.mapping(ta -> ArticleResponse.from(ta.getArticle()), Collectors.toList())
                    ));
        }

        List<TopicComparisonResponse.Group> groups = grouped.entrySet().stream()
                .map(e -> TopicComparisonResponse.Group.builder()
                        .key(e.getKey())
                        .articles(e.getValue())
                        .build())
                .toList();

        return TopicComparisonResponse.builder()
                .topicId(topicId)
                .groupBy(groupBy == null ? "leaning" : groupBy.toLowerCase())
                .groups(groups)
                .build();
    }

    Topic getTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found: " + topicId));
    }
}
