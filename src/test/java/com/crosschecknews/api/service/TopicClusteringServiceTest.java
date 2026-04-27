package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ClusteringRequest;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * TopicClusteringService — TF-IDF + Cosine Similarity 클러스터링 테스트.
 *
 * <p>외부 API 의존 없이 순수 TF-IDF 벡터로 유사도를 계산하므로,
 * 테스트는 실제 headline 텍스트로 동작을 검증한다.
 *
 * <h3>TF-IDF 특성</h3>
 * <ul>
 *   <li>같은 이슈 headline: 핵심 단어 공유 → cosine > 0</li>
 *   <li>다른 이슈 headline: 공유 단어 없음 → cosine = 0.0</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TopicClusteringServiceTest {

    @Mock ArticleRepository      articleRepository;
    @Mock TopicRepository        topicRepository;
    @Mock TopicArticleRepository topicArticleRepository;

    @InjectMocks TopicClusteringService clusteringService;

    @BeforeEach
    void setUp() {
        // TF-IDF cosine은 0.1~0.4 범위가 일반적
        ReflectionTestUtils.setField(clusteringService, "similarityThreshold", 0.10);
        ReflectionTestUtils.setField(clusteringService, "minPublishers", 2);
    }

    private Publisher publisher(long id, String name) {
        return Publisher.builder()
                .id(id).name(name)
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    private Article article(long id, Publisher publisher, String headline) {
        return Article.builder()
                .id(id)
                .headline(headline)
                .normalizedHeadline(headline.toLowerCase())
                .url("https://example.com/" + id)
                .normalizedUrl("https://example.com/" + id)
                .dedupeKey("key-" + id)
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .publishedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    private void stubTopicSave(long topicId) {
        given(topicRepository.save(any(Topic.class))).willAnswer(inv -> {
            Topic t = inv.getArgument(0);
            return Topic.builder().id(topicId).title(t.getTitle())
                    .articleCategory(t.getArticleCategory()).status(t.getStatus())
                    .startDate(t.getStartDate()).build();
        });
    }

    private ClusteringRequest req(int fromHours) {
        return new ClusteringRequest(ArticleCategory.WORLD, fromHours);
    }

    // ── 후보 기사 부족 ─────────────────────────────────────────────────────────

    @Test
    void 후보_기사가_1개이면_클러스터_없이_빈_결과_반환() {
        Publisher pub = publisher(1L, "Fox News");
        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(article(1L, pub, "trump wins election")));

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository, topicArticleRepository);
    }

    @Test
    void 후보_기사가_없으면_빈_결과_반환() {
        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of());

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository);
    }

    // ── 클러스터 생성 ──────────────────────────────────────────────────────────

    @Test
    void 핵심단어_공유_headline은_같은_클러스터로_묶인다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");
        // "trump", "election" 공유 → TF-IDF cosine > 0
        Article a1 = article(1L, fox, "trump wins us election");
        Article a2 = article(2L, nyt, "trump wins presidential election");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        stubTopicSave(1L);
        given(topicArticleRepository.existsByTopicIdAndArticleId(any(), any()))
                .willReturn(false);

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(1);
        assertThat(result.getLinkedArticleCount()).isEqualTo(2);
        verify(topicRepository).save(any(Topic.class));
        verify(topicArticleRepository, times(2)).save(any(TopicArticle.class));
    }

    // ── 단일 언론사 클러스터 필터링 ────────────────────────────────────────────

    @Test
    void 같은_언론사_기사끼리만_묶이면_Topic이_생성되지_않는다() {
        Publisher fox = publisher(1L, "Fox News");
        Article a1 = article(1L, fox, "trump wins us election");
        Article a2 = article(2L, fox, "trump wins presidential election");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository);
    }

    @Test
    void 공유단어가_없는_headline은_클러스터로_묶이지_않는다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");
        // 완전히 다른 이슈 — 공유 단어 없음 → cosine = 0.0
        Article a1 = article(1L, fox, "trump wins election");
        Article a2 = article(2L, nyt, "japan earthquake disaster");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository);
    }

    // ── 복수 클러스터 ──────────────────────────────────────────────────────────

    @Test
    void 두_이슈의_기사가_각각_다른_클러스터로_분리된다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");

        Article a1 = article(1L, fox, "trump wins us election president");
        Article a2 = article(2L, nyt, "trump wins presidential election us");
        Article a3 = article(3L, fox, "japan earthquake disaster rescue");
        Article a4 = article(4L, nyt, "earthquake strikes japan rescue teams");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2, a3, a4));

        given(topicRepository.save(any(Topic.class))).willAnswer(inv -> {
            Topic t = inv.getArgument(0);
            long id = t.getTitle().toLowerCase().contains("trump") ? 1L : 2L;
            return Topic.builder().id(id).title(t.getTitle())
                    .articleCategory(t.getArticleCategory()).status(t.getStatus())
                    .startDate(t.getStartDate()).build();
        });
        given(topicArticleRepository.existsByTopicIdAndArticleId(any(), any()))
                .willReturn(false);

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(2);
        assertThat(result.getLinkedArticleCount()).isEqualTo(4);
        verify(topicRepository, times(2)).save(any(Topic.class));
    }

    // ── 이미 연결된 기사 중복 방지 ─────────────────────────────────────────────

    @Test
    void 이미_Topic에_연결된_기사는_중복_저장하지_않는다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");
        Article a1 = article(1L, fox, "trump wins us election");
        Article a2 = article(2L, nyt, "trump wins presidential election");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        stubTopicSave(1L);
        given(topicArticleRepository.existsByTopicIdAndArticleId(any(), any()))
                .willReturn(true)   // a1: 이미 연결
                .willReturn(false); // a2: 신규

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getLinkedArticleCount()).isEqualTo(1);
        verify(topicArticleRepository, times(1)).save(any(TopicArticle.class));
    }
}
