package com.crosschecknews.api.integration;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ArticleApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TopicArticleRepository topicArticleRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired PublisherFeedRepository publisherFeedRepository;
    @Autowired PublisherRepository publisherRepository;

    @BeforeEach
    void setUp() {
        topicArticleRepository.deleteAll();
        topicRepository.deleteAll();
        articleRepository.deleteAll();
        publisherFeedRepository.deleteAll();
        publisherRepository.deleteAll();
    }

    @Test
    void 기사_목록_조회_초기에는_비어있음() throws Exception {
        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
    }

    @Test
    void 기사_저장_후_목록에서_조회됨() throws Exception {
        Publisher publisher = publisherRepository.save(Publisher.builder()
                .name("NYT")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.PROGRESSIVE)
                .build());

        articleRepository.save(Article.builder()
                .headline("Global Trade War Escalates")
                .normalizedHeadline("global trade war escalates")
                .url("https://nytimes.com/article/trade")
                .normalizedUrl("https://nytimes.com/article/trade")
                .rssGuid("nyt-guid-001")
                .dedupeKey("nyt-dedupe-001")
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build());

        mockMvc.perform(get("/api/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].headline").value("Global Trade War Escalates"))
                .andExpect(jsonPath("$.items[0].publisherName").value("NYT"));
    }

    @Test
    void headline_필터로_해당_키워드_포함_기사만_조회됨() throws Exception {
        Publisher nyt = publisherRepository.save(Publisher.builder()
                .name("NYT")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.PROGRESSIVE)
                .build());

        Publisher fox = publisherRepository.save(Publisher.builder()
                .name("Fox News")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build());

        articleRepository.save(Article.builder()
                .headline("Global Trade War Escalates")
                .normalizedHeadline("global trade war escalates")
                .url("https://nytimes.com/1")
                .normalizedUrl("https://nytimes.com/1")
                .rssGuid("nyt-1")
                .dedupeKey("dedupe-nyt-1")
                .publisher(nyt)
                .category(ArticleCategory.WORLD)
                .build());

        articleRepository.save(Article.builder()
                .headline("Fox Politics Coverage")
                .normalizedHeadline("fox politics coverage")
                .url("https://foxnews.com/1")
                .normalizedUrl("https://foxnews.com/1")
                .rssGuid("fox-1")
                .dedupeKey("dedupe-fox-1")
                .publisher(fox)
                .category(ArticleCategory.WORLD)
                .build());

        // "Trade" 키워드로 필터링 — NYT 기사 1건만 일치
        mockMvc.perform(get("/api/v1/articles").param("headline", "Trade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].headline").value("Global Trade War Escalates"));
    }

    @Test
    void 기사_단건_조회_정상() throws Exception {
        Publisher publisher = publisherRepository.save(Publisher.builder()
                .name("BBC")
                .country(Country.GB)
                .politicalLeaning(PoliticalLeaning.PROGRESSIVE)
                .build());

        Article saved = articleRepository.save(Article.builder()
                .headline("BBC Breaking News")
                .normalizedHeadline("bbc breaking news")
                .url("https://bbc.com/news/1")
                .normalizedUrl("https://bbc.com/news/1")
                .rssGuid("bbc-guid-001")
                .dedupeKey("bbc-dedupe-001")
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build());

        mockMvc.perform(get("/api/v1/articles/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("BBC Breaking News"))
                .andExpect(jsonPath("$.publisherName").value("BBC"));
    }

    @Test
    void 존재하지_않는_기사_조회시_404_반환() throws Exception {
        mockMvc.perform(get("/api/v1/articles/99999"))
                .andExpect(status().isNotFound());
    }
}
