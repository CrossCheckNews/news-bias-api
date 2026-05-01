package com.crosschecknews.api.integration;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TopicApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TopicArticleRepository topicArticleRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired PublisherFeedRepository publisherFeedRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired PipelineStepHistoryRepository pipelineStepHistoryRepository;
    @Autowired PipelineRunRepository pipelineRunRepository;

    @BeforeEach
    void setUp() {
        pipelineStepHistoryRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        topicArticleRepository.deleteAll();
        topicRepository.deleteAll();
        articleRepository.deleteAll();
        publisherFeedRepository.deleteAll();
        publisherRepository.deleteAll();
    }

    @Test
    void 존재하지_않는_topic_조회시_404_반환() throws Exception {
        mockMvc.perform(get("/api/v1/topics/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void topic_status_필터_조회() throws Exception {
        topicRepository.save(Topic.builder()
                .title("ACTIVE 토픽")
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ACTIVE)
                .build());

        topicRepository.save(Topic.builder()
                .title("ARCHIVED 토픽")
                .articleCategory(ArticleCategory.WORLD)
                .status(TopicStatus.ARCHIVED)
                .build());

        mockMvc.perform(get("/api/v1/topics").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }
}
