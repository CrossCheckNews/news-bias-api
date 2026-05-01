package com.crosschecknews.api.integration;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired ObjectMapper objectMapper;
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
    void topic_생성_조회_수정_삭제_전체_흐름() throws Exception {
        // 생성
        String createBody = """
                {
                  "title": "미국 관세 정책 논란",
                  "articleCategory": "WORLD",
                  "status": "ACTIVE",
                  "startDate": "2026-04-01"
                }
                """;

        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiSummaryTitle").value("미국 관세 정책 논란"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").isNumber());

        // 목록 조회 — 방금 생성한 토픽이 포함됨
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].aiSummaryTitle").value("미국 관세 정책 논란"));

        // 단건 조회
        String topicIdStr = mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "조회용 토픽",
                                  "articleCategory": "WORLD",
                                  "status": "PENDING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long topicId = objectMapper.readTree(topicIdStr).get("id").asLong();

        // 단건 조회는 TopicDetailResponse → "title" 필드 사용
        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId))
                .andExpect(jsonPath("$.title").value("조회용 토픽"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 수정 응답은 TopicResponse → "aiSummaryTitle" 필드 사용
        mockMvc.perform(put("/api/v1/topics/" + topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정된 토픽",
                                  "articleCategory": "WORLD",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSummaryTitle").value("수정된 토픽"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 삭제 후 404
        mockMvc.perform(delete("/api/v1/topics/" + topicId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isNotFound());
    }

    @Test
    void topic_제목_없이_생성하면_400_반환() throws Exception {
        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "articleCategory": "WORLD",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_topic_조회시_404_반환() throws Exception {
        mockMvc.perform(get("/api/v1/topics/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void topic에_기사_연결_후_상세_조회시_기사가_포함됨() throws Exception {
        // Publisher, Article 세팅
        Publisher publisher = publisherRepository.save(Publisher.builder()
                .name("Test Publisher")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.PROGRESSIVE)
                .build());

        Article article = articleRepository.save(Article.builder()
                .headline("Test Headline")
                .normalizedHeadline("test headline")
                .url("https://example.com/article/1")
                .normalizedUrl("https://example.com/article/1")
                .rssGuid("guid-001")
                .dedupeKey("dedupe-001")
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build());

        // Topic 생성
        String topicJson = mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "기사 연결 테스트 토픽",
                                  "articleCategory": "WORLD",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long topicId = objectMapper.readTree(topicJson).get("id").asLong();

        // 기사 연결
        mockMvc.perform(post("/api/v1/topics/" + topicId + "/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\": " + article.getId() + "}"))
                .andExpect(status().isCreated());

        // 상세 조회 — 기사 포함 확인
        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.articles[0].headline").value("Test Headline"))
                .andExpect(jsonPath("$.articles[0].publisherName").value("Test Publisher"));
    }

    @Test
    void topic에_연결된_기사_해제_후_조회시_기사가_제외됨() throws Exception {
        Publisher publisher = publisherRepository.save(Publisher.builder()
                .name("Unlink Publisher")
                .country(Country.KR)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build());

        Article article = articleRepository.save(Article.builder()
                .headline("Unlink Headline")
                .normalizedHeadline("unlink headline")
                .url("https://example.com/article/unlink")
                .normalizedUrl("https://example.com/article/unlink")
                .rssGuid("guid-unlink")
                .dedupeKey("dedupe-unlink")
                .publisher(publisher)
                .category(ArticleCategory.WORLD)
                .build());

        String topicJson = mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "해제 테스트 토픽",
                                  "articleCategory": "WORLD",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long topicId = objectMapper.readTree(topicJson).get("id").asLong();

        // 연결
        mockMvc.perform(post("/api/v1/topics/" + topicId + "/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\": " + article.getId() + "}"))
                .andExpect(status().isCreated());

        // 해제
        mockMvc.perform(delete("/api/v1/topics/" + topicId + "/articles/" + article.getId()))
                .andExpect(status().isNoContent());

        // 기사 제외 확인
        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleCount").value(0));
    }

    @Test
    void topic_status_필터_조회() throws Exception {
        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "ACTIVE 토픽", "articleCategory": "WORLD", "status": "ACTIVE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "ARCHIVED 토픽", "articleCategory": "WORLD", "status": "ARCHIVED"}
                                """))
                .andExpect(status().isCreated());

        // ACTIVE만 필터
        mockMvc.perform(get("/api/v1/topics").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].aiSummaryTitle").value("ACTIVE 토픽"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }
}
