package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DashboardRepositoryTest {

    @Autowired ArticleRepository articleRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired TopicArticleRepository topicArticleRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired PipelineRunRepository pipelineRunRepository;
    @Autowired PipelineStepHistoryRepository pipelineStepHistoryRepository;

    // ── countArticlesByPublisher ────────────────────────────────────────────

    @Test
    void publisher별_기사_수가_내림차순으로_집계된다() {
        Publisher nyt = savedPublisher("NYT", Country.US);
        Publisher fox = savedPublisher("Fox News", Country.US);

        savedArticle(nyt, "nyt-1");
        savedArticle(nyt, "nyt-2");
        savedArticle(nyt, "nyt-3");
        savedArticle(fox, "fox-1");
        savedArticle(fox, "fox-2");

        List<Object[]> result = articleRepository.countArticlesByPublisher();

        assertThat(result).hasSize(2);
        assertThat((String) result.get(0)[0]).isEqualTo("NYT");
        assertThat((Long) result.get(0)[1]).isEqualTo(3L);
        assertThat((String) result.get(1)[0]).isEqualTo("Fox News");
        assertThat((Long) result.get(1)[1]).isEqualTo(2L);
    }

    @Test
    void 기사가_없으면_publisher별_집계_결과가_비어있다() {
        List<Object[]> result = articleRepository.countArticlesByPublisher();
        assertThat(result).isEmpty();
    }

    // ── countTopicsByCountry ────────────────────────────────────────────────

    @Test
    void country별_토픽_수가_집계된다() {
        Publisher usPublisher = savedPublisher("Fox News", Country.US);
        Publisher krPublisher = savedPublisher("Chosun", Country.KR);

        Article usArticle = savedArticle(usPublisher, "us-1");
        Article krArticle = savedArticle(krPublisher, "kr-1");

        Topic topic1 = savedTopic("US Topic");
        Topic topic2 = savedTopic("KR Topic");

        topicArticleRepository.save(TopicArticle.builder().topic(topic1).article(usArticle).build());
        topicArticleRepository.save(TopicArticle.builder().topic(topic2).article(krArticle).build());

        List<Object[]> result = topicRepository.countTopicsByCountry();

        assertThat(result).hasSize(2);
        assertThat(result.stream().mapToLong(row -> (Long) row[1]).sum()).isEqualTo(2L);
    }

    @Test
    void 같은_country의_여러_기사가_연결된_토픽도_distinct로_집계된다() {
        Publisher publisher = savedPublisher("NYT", Country.US);
        Article article1 = savedArticle(publisher, "a1");
        Article article2 = savedArticle(publisher, "a2");

        Topic topic = savedTopic("Shared Topic");
        topicArticleRepository.save(TopicArticle.builder().topic(topic).article(article1).build());
        topicArticleRepository.save(TopicArticle.builder().topic(topic).article(article2).build());

        List<Object[]> result = topicRepository.countTopicsByCountry();

        assertThat(result).hasSize(1);
        // 기사 2개가 같은 topic에 연결됐어도 topic count는 1
        assertThat((Long) result.get(0)[1]).isEqualTo(1L);
    }

    // ── countByFetchedAtBetween ─────────────────────────────────────────────

    @Test
    void 오늘_수집된_기사_수가_날짜_범위로_집계된다() {
        Publisher publisher = savedPublisher("Test Publisher", Country.US);
        savedArticle(publisher, "today-1");
        savedArticle(publisher, "today-2");

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        long count = articleRepository.countByFetchedAtBetween(start, end);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void 어제_날짜_범위로_조회하면_오늘_수집된_기사가_집계되지_않는다() {
        Publisher publisher = savedPublisher("Old Publisher", Country.US);
        savedArticle(publisher, "today-only");

        // fetchedAt은 @PrePersist에서 now()로 설정되므로 어제 범위에는 포함되지 않음
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long count = articleRepository.countByFetchedAtBetween(yesterdayStart, todayStart);

        assertThat(count).isEqualTo(0L);
    }

    // ── countByStatus (PipelineStepHistoryRepository) ───────────────────────

    @Test
    void FAILED_상태의_PipelineStepHistory_수만_집계된다() {
        PipelineRun run = savedPipelineRun(PipelineStatus.FAILED);

        pipelineStepHistoryRepository.saveAll(List.of(
                stepHistory(run, PipelineStatus.FAILED),
                stepHistory(run, PipelineStatus.FAILED),
                stepHistory(run, PipelineStatus.SUCCESS)
        ));

        assertThat(pipelineStepHistoryRepository.countByStatus(PipelineStatus.FAILED)).isEqualTo(2L);
        assertThat(pipelineStepHistoryRepository.countByStatus(PipelineStatus.SUCCESS)).isEqualTo(1L);
    }

    // ── countByStatus (PipelineRunRepository) ──────────────────────────────

    @Test
    void PipelineRun_status별_count가_정확하다() {
        savedPipelineRun(PipelineStatus.SUCCESS);
        savedPipelineRun(PipelineStatus.SUCCESS);
        savedPipelineRun(PipelineStatus.FAILED);

        assertThat(pipelineRunRepository.countByStatus(PipelineStatus.SUCCESS)).isEqualTo(2L);
        assertThat(pipelineRunRepository.countByStatus(PipelineStatus.FAILED)).isEqualTo(1L);
        assertThat(pipelineRunRepository.countByStatus(PipelineStatus.RUNNING)).isEqualTo(0L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Publisher savedPublisher(String name, Country country) {
        return publisherRepository.save(Publisher.builder()
                .name(name)
                .country(country)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build());
    }

    private Article savedArticle(Publisher publisher, String key) {
        return articleRepository.save(Article.builder()
                .headline("Headline " + key)
                .normalizedHeadline("headline " + key)
                .url("https://test.com/" + key)
                .normalizedUrl("https://test.com/" + key)
                .rssGuid("guid-" + key)
                .dedupeKey("dedupe-" + key)
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build());
    }

    private Topic savedTopic(String title) {
        return topicRepository.save(Topic.builder()
                .title(title)
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ACTIVE)
                .startDate(LocalDate.now())
                .build());
    }

    private PipelineRun savedPipelineRun(PipelineStatus status) {
        return pipelineRunRepository.save(PipelineRun.builder()
                .status(status)
                .fetchedCount(5).savedCount(5).clusteredCount(0).summarizedCount(0)
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .finishedAt(LocalDateTime.now())
                .build());
    }

    private PipelineStepHistory stepHistory(PipelineRun run, PipelineStatus status) {
        return PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(PipelineStep.RSS_COLLECT)
                .status(status)
                .processedCount(0)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .finishedAt(LocalDateTime.now())
                .build();
    }
}
