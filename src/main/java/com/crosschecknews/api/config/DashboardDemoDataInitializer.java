package com.crosschecknews.api.config;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PipelineRun;
import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.domain.PipelineStepHistory;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.repository.PipelineRunRepository;
import com.crosschecknews.api.repository.PipelineStepHistoryRepository;
import com.crosschecknews.api.repository.PublisherRepository;
import com.crosschecknews.api.repository.TopicArticleRepository;
import com.crosschecknews.api.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DashboardDemoDataInitializer implements ApplicationRunner {

    private final PublisherRepository publisherRepository;
    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final PipelineStepHistoryRepository pipelineStepHistoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (articleRepository.count() > 0 || topicRepository.count() > 0) {
            return;
        }

        Publisher fox = publisher("Fox News", Country.US, PoliticalLeaning.CONSERVATIVE);
        Publisher nyt = publisher("New York Times", Country.US, PoliticalLeaning.PROGRESSIVE);
        Publisher chosun = publisher("Chosun Ilbo", Country.KR, PoliticalLeaning.CONSERVATIVE);
        Publisher hani = publisher("Hankyoreh", Country.KR, PoliticalLeaning.PROGRESSIVE);

        List<Article> tradeArticles = articleRepository.saveAll(List.of(
                article("US tariff plan sparks market volatility", fox, 0, "trade-fox"),
                article("Tariff push rattles allies and global markets", nyt, 0, "trade-nyt"),
                article("미 관세 정책, 한국 수출 업계 긴장", chosun, 0, "trade-chosun"),
                article("관세 갈등 장기화에 세계 경제 우려", hani, 0, "trade-hani")
        ));

        List<Article> climateArticles = articleRepository.saveAll(List.of(
                article("Leaders debate climate financing framework", nyt, 1, "climate-nyt"),
                article("G7 climate pledge faces domestic pushback", fox, 1, "climate-fox"),
                article("기후 재원 합의안, 한국 산업계 영향 주목", chosun, 1, "climate-chosun")
        ));

        Topic tradeTopic = topicRepository.save(topic(
                "Global tariff tensions reshape trade talks",
                "미국의 관세 정책을 둘러싸고 글로벌 시장과 동맹국의 긴장이 커지고 있습니다.",
                0
        ));
        Topic climateTopic = topicRepository.save(topic(
                "Climate financing debate intensifies at summit",
                "각국 정상들이 기후 재원 조달 방식을 두고 이견을 좁히는 중입니다.",
                1
        ));

        link(tradeTopic, tradeArticles);
        link(climateTopic, climateArticles);

        LocalDateTime now = LocalDateTime.now();
        PipelineRun latestRun = run(PipelineStatus.SUCCESS, 12, 7, 2, 2, "정기 수집 완료", null, now.minusMinutes(35), now.minusMinutes(33));
        PipelineRun failedRun = run(PipelineStatus.FAILED, 5, 0, 0, 0, "일부 단계 실패", "Fox News RSS timeout", now.minusHours(7), now.minusHours(7).plusMinutes(2));
        PipelineRun oldRun = run(PipelineStatus.SUCCESS, 15, 11, 3, 3, "정기 수집 완료", null, now.minusDays(1), now.minusDays(1).plusMinutes(3));

        pipelineStepHistoryRepository.saveAll(List.of(
                step(latestRun, PipelineStep.RSS_COLLECT, PipelineStatus.SUCCESS, "PUBLISHER_FEED", "Fox News", 4, null, null, "RSS 피드 수집 완료", now.minusMinutes(35), now.minusMinutes(34)),
                step(latestRun, PipelineStep.ARTICLE_SAVE, PipelineStatus.SUCCESS, "ARTICLE", "NORMALIZED_ARTICLES", 7, null, null, "기사 저장 완료", now.minusMinutes(34), now.minusMinutes(34)),
                step(latestRun, PipelineStep.TOPIC_CLUSTERING, PipelineStatus.SUCCESS, "ARTICLE_CATEGORY", "WORLD", 2, null, null, "토픽 클러스터링 완료", now.minusMinutes(34), now.minusMinutes(33)),
                step(latestRun, PipelineStep.AI_SUMMARY, PipelineStatus.SUCCESS, "TOPIC", "ACTIVE_TOPICS", 2, null, null, "AI 요약 생성 완료", now.minusMinutes(33), now.minusMinutes(33)),
                step(failedRun, PipelineStep.RSS_COLLECT, PipelineStatus.FAILED, "PUBLISHER_FEED", "Fox News", 5, "RSS_TIMEOUT", "RSS 피드 응답 지연", "RSS 피드 수집 실패", now.minusHours(7), now.minusHours(7).plusMinutes(2)),
                step(failedRun, PipelineStep.ARTICLE_SAVE, PipelineStatus.FAILED, "ARTICLE", "NORMALIZED_ARTICLES", 0, "UPSTREAM_FAILED", "RSS 수집 실패로 저장할 신규 기사가 없습니다.", "기사 저장 건너뜀", now.minusHours(7).plusMinutes(2), now.minusHours(7).plusMinutes(2)),
                step(oldRun, PipelineStep.TOPIC_CLUSTERING, PipelineStatus.SUCCESS, "ARTICLE_CATEGORY", "WORLD", 3, null, null, "토픽 클러스터링 완료", now.minusDays(1), now.minusDays(1).plusMinutes(3))
        ));
    }

    private Publisher publisher(String name, Country country, PoliticalLeaning leaning) {
        return publisherRepository.findByName(name)
                .orElseGet(() -> publisherRepository.save(Publisher.builder()
                        .name(name)
                        .country(country)
                        .politicalLeaning(leaning)
                        .build()));
    }

    private Article article(String headline, Publisher publisher, int daysAgo, String key) {
        return Article.builder()
                .headline(headline)
                .normalizedHeadline(headline.toLowerCase())
                .url("https://demo.crosscheck.news/articles/" + key)
                .normalizedUrl("https://demo.crosscheck.news/articles/" + key)
                .description(headline + " - demo monitoring seed article")
                .publishedAt(LocalDateTime.now().minusDays(daysAgo).minusHours(2))
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .rssGuid("demo-" + key)
                .dedupeKey("demo-" + key)
                .build();
    }

    private Topic topic(String title, String summary, int daysAgo) {
        return Topic.builder()
                .title(title)
                .summary(summary)
                .aiSummary(summary)
                .summaryGeneratedAt(LocalDateTime.now().minusDays(daysAgo).minusMinutes(20))
                .aiModel("demo-seed")
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(daysAgo))
                .build();
    }

    private void link(Topic topic, List<Article> articles) {
        topicArticleRepository.saveAll(articles.stream()
                .map(article -> TopicArticle.builder()
                        .topic(topic)
                        .article(article)
                        .build())
                .toList());
    }

    private PipelineRun run(
            PipelineStatus status,
            int fetched,
            int saved,
            int clustered,
            int summarized,
            String message,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        return pipelineRunRepository.save(PipelineRun.builder()
                .status(status)
                .fetchedCount(fetched)
                .savedCount(saved)
                .clusteredCount(clustered)
                .summarizedCount(summarized)
                .message(message)
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build());
    }

    private PipelineStepHistory step(
            PipelineRun run,
            PipelineStep step,
            PipelineStatus status,
            String targetType,
            String targetName,
            int processedCount,
            String errorType,
            String errorMessage,
            String message,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        return PipelineStepHistory.builder()
                .pipelineRun(run)
                .step(step)
                .status(status)
                .targetType(targetType)
                .targetName(targetName)
                .processedCount(processedCount)
                .successCount(status == PipelineStatus.SUCCESS ? processedCount : 0)
                .failedCount(status == PipelineStatus.FAILED ? 1 : 0)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .message(message)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();
    }
}
