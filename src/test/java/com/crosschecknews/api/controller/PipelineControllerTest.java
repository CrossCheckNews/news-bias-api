package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.ArticleCategory;
import com.crosschecknews.api.dto.ClusteringResult;
import com.crosschecknews.api.dto.FetchAndSaveResult;
import com.crosschecknews.api.dto.PipelineResult;
import com.crosschecknews.api.dto.PipelineRunLatestResponse;
import com.crosschecknews.api.dto.SummarizeResponse;
import com.crosschecknews.api.service.NewsIngestionPipelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PipelineController.class)
class PipelineControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean NewsIngestionPipelineService pipelineService;

    private PipelineResult stubResult(int fetched, int saved) {
        FetchAndSaveResult fetchAndSave = FetchAndSaveResult.builder()
                .fetchedCount(fetched)
                .savedCount(saved)
                .duplicateCount(0)
                .failedCount(0)
                .feeds(List.of())
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();

        ClusteringResult clustering = ClusteringResult.builder()
                .category(ArticleCategory.WORLD)
                .fromHours(48)
                .scannedArticleCount(4)
                .clustersCreated(2)
                .linkedArticleCount(4)
                .topics(List.of())
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();

        SummarizeResponse summary = SummarizeResponse.builder()
                .topicId(1L)
                .title("Test Topic")
                .summary("Test summary")
                .aiModel("gemini")
                .generatedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();

        return PipelineResult.builder()
                .fetchAndSave(fetchAndSave)
                .clustering(List.of(clustering))
                .summaries(List.of(summary))
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();
    }

    @Test
    void 요청_바디_없이_호출하면_기본값으로_파이프라인이_실행된다() throws Exception {
        given(pipelineService.run(isNull())).willReturn(stubResult(10, 8));

        mockMvc.perform(post("/api/v1/pipeline/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchAndSave.fetchedCount").value(10))
                .andExpect(jsonPath("$.fetchAndSave.savedCount").value(8))
                .andExpect(jsonPath("$.clustering[0].clustersCreated").value(2))
                .andExpect(jsonPath("$.summaries.length()").value(1));

        verify(pipelineService).run(null);
    }

    @Test
    void fromHours를_지정해서_호출하면_해당_값으로_파이프라인이_실행된다() throws Exception {
        given(pipelineService.run(any())).willReturn(stubResult(5, 5));

        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchAndSave.fetchedCount").value(5))
                .andExpect(jsonPath("$.fetchAndSave.savedCount").value(5));
    }

    @Test
    void fromHours가_0이면_400_Bad_Request를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromHours가_168을_초과하면_400_Bad_Request를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 169}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 최근_파이프라인_실행일을_오늘날짜와_함께_반환한다() throws Exception {
        given(pipelineService.getLatestRunDate()).willReturn(
                PipelineRunLatestResponse.builder()
                        .runDate("2026-04-30")
                        .today("2026-05-01")
                        .build());

        mockMvc.perform(get("/api/v1/pipeline/latest-run-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runDate").value("2026-04-30"))
                .andExpect(jsonPath("$.today").value("2026-05-01"));
    }

    @Test
    void 파이프라인_실행_이력이_없으면_runDate가_null이다() throws Exception {
        given(pipelineService.getLatestRunDate()).willReturn(
                PipelineRunLatestResponse.builder()
                        .runDate(null)
                        .today("2026-05-01")
                        .build());

        mockMvc.perform(get("/api/v1/pipeline/latest-run-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runDate").doesNotExist())
                .andExpect(jsonPath("$.today").value("2026-05-01"));
    }

    @Test
    void 파이프라인_결과에_피드별_수집실패_정보가_포함된다() throws Exception {
        FetchAndSaveResult.FeedSummary failedFeed = FetchAndSaveResult.FeedSummary.builder()
                .feedSourceCode("BBC_WORLD")
                .publisherName("BBC")
                .collectSuccess(false)
                .fetched(0).saved(0).duplicates(0).failed(0)
                .errorMessage("Connection timeout")
                .build();

        FetchAndSaveResult fetchAndSave = FetchAndSaveResult.builder()
                .fetchedCount(0)
                .savedCount(0)
                .duplicateCount(0)
                .failedCount(1)
                .feeds(List.of(failedFeed))
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();

        PipelineResult result = PipelineResult.builder()
                .fetchAndSave(fetchAndSave)
                .clustering(List.of())
                .summaries(List.of())
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();

        given(pipelineService.run(any())).willReturn(result);

        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 48}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchAndSave.feeds[0].feedSourceCode").value("BBC_WORLD"))
                .andExpect(jsonPath("$.fetchAndSave.feeds[0].collectSuccess").value(false));
    }
}
