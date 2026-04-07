package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ClusteringRequest;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Stage 4: Topic 클러스터링 테스트.
 *
 * 핵심 로직:
 * - 유사도 >= 0.25이면 같은 클러스터로 Union
 * - 서로 다른 언론사 2개 이상 있어야 유효 클러스터
 * - 유효 클러스터 → Topic + TopicArticle 저장
 */
@ExtendWith(MockitoExtension.class)
class TopicClusteringServiceTest {

    @Mock ArticleRepository      articleRepository;
    @Mock TopicRepository        topicRepository;
    @Mock TopicArticleRepository topicArticleRepository;
    @Mock HeadlineSimilarityService similarityService;

    @InjectMocks TopicClusteringService clusteringService;

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
                .category(Category.WORLD)
                .publishedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    /** topicRepository.save()가 @GeneratedValue 없이 null id를 반환하므로, id를 직접 주입한 Topic 반환 */
    private void stubTopicSave(long topicId) {
        given(topicRepository.save(any(Topic.class))).willAnswer(inv -> {
            Topic t = inv.getArgument(0);
            return Topic.builder().id(topicId).title(t.getTitle())
                    .category(t.getCategory()).status(t.getStatus())
                    .startDate(t.getStartDate()).build();
        });
    }

    private ClusteringRequest req(int fromHours) {
        return new ClusteringRequest(Category.WORLD, fromHours);
    }

    // ── 후보 기사 부족 ─────────────────────────────────────────────────────────

    @Test
    void 후보_기사가_1개이면_클러스터_없이_빈_결과_반환() {
        Publisher pub = publisher(1L, "Fox News");
        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(article(1L, pub, "trump wins election")));

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        assertThat(result.getLinkedArticleCount()).isEqualTo(0);
        verifyNoInteractions(topicRepository, topicArticleRepository, similarityService);
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
    void 유사도_임계값_이상이고_다른_언론사_2개면_Topic이_생성된다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");
        Article a1 = article(1L, fox, "trump wins us election");
        Article a2 = article(2L, nyt, "trump elected us president");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        // buildClusters + pickRepresentativeTitle 모두 처리 — any() 사용
        given(similarityService.similarity(any(), any())).willReturn(0.5);
        stubTopicSave(1L);
        // topic.getId()가 null이 되므로 any(Long.class) 아닌 any() 사용
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
        Article a2 = article(2L, fox, "trump elected us president");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        given(similarityService.similarity(any(), any())).willReturn(0.8);

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository);
    }

    @Test
    void 유사도가_임계값_미만이면_클러스터로_묶이지_않는다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");
        Article a1 = article(1L, fox, "trump wins election");
        Article a2 = article(2L, nyt, "japan earthquake disaster");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        given(similarityService.similarity(any(), any())).willReturn(0.0);

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getClustersCreated()).isEqualTo(0);
        verifyNoInteractions(topicRepository);
    }

    // ── 복수 클러스터 ──────────────────────────────────────────────────────────

    @Test
    void 두_이슈의_기사가_각각_다른_클러스터로_분리된다() {
        Publisher fox = publisher(1L, "Fox News");
        Publisher nyt = publisher(2L, "New York Times");

        Article a1 = article(1L, fox, "trump wins election");
        Article a2 = article(2L, nyt, "trump elected president");
        Article a3 = article(3L, fox, "japan earthquake disaster");
        Article a4 = article(4L, nyt, "earthquake strikes japan");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2, a3, a4));

        // 키워드 기반 Answer: trump 기사끼리, earthquake 기사끼리는 유사 / 교차는 비유사
        given(similarityService.similarity(any(), any())).willAnswer(inv -> {
            String h1 = inv.getArgument(0);
            String h2 = inv.getArgument(1);
            boolean bothTrump = h1.contains("trump") && h2.contains("trump");
            boolean bothQuake = (h1.contains("earthquake") || h1.contains("japan"))
                    && (h2.contains("earthquake") || h2.contains("japan"));
            return (bothTrump || bothQuake) ? 0.5 : 0.0;
        });

        // 두 번 호출되므로 각각 다른 id 반환
        given(topicRepository.save(any(Topic.class))).willAnswer(inv -> {
            Topic t = inv.getArgument(0);
            // title로 id 구분
            long id = t.getTitle().contains("trump") ? 1L : 2L;
            return Topic.builder().id(id).title(t.getTitle())
                    .category(t.getCategory()).status(t.getStatus())
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
        Article a1 = article(1L, fox, "trump wins election");
        Article a2 = article(2L, nyt, "trump elected president");

        given(articleRepository.findClusteringCandidates(any(), any()))
                .willReturn(List.of(a1, a2));
        given(similarityService.similarity(any(), any())).willReturn(0.5);
        stubTopicSave(1L);
        // topic.getId()는 null(저장 결과를 재할당하지 않음) → any() 사용
        // a1(id=1)은 이미 연결, a2(id=2)는 신규 — id 순서로 순회하므로 첫 번째 false, 두 번째 true
        given(topicArticleRepository.existsByTopicIdAndArticleId(any(), any()))
                .willReturn(true)   // a1: 이미 연결
                .willReturn(false); // a2: 신규

        ClusteringResult result = clusteringService.cluster(req(48));

        assertThat(result.getLinkedArticleCount()).isEqualTo(1);
        verify(topicArticleRepository, times(1)).save(any(TopicArticle.class));
    }
}
