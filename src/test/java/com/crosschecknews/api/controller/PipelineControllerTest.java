package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.PipelineResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PipelineController.class)
class PipelineControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean NewsIngestionPipelineService pipelineService;

    private PipelineResult stubResult(int fetched, int saved) {
        return PipelineResult.builder()
                .fetchedCount(fetched)
                .savedCount(saved)
                .duplicateCount(0)
                .clustersCreated(2)
                .linkedArticleCount(4)
                .summariesGenerated(2)
                .failedSources(List.of())
                .failedCategories(List.of())
                .summaryFailedCount(0)
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();
    }

    @Test
    void 요청_바디_없이_호출하면_기본값으로_파이프라인이_실행된다() throws Exception {
        given(pipelineService.run(isNull())).willReturn(stubResult(10, 8));

        mockMvc.perform(post("/api/v1/pipeline/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedCount").value(10))
                .andExpect(jsonPath("$.savedCount").value(8))
                .andExpect(jsonPath("$.clustersCreated").value(2))
                .andExpect(jsonPath("$.summariesGenerated").value(2));

        verify(pipelineService).run(null);
    }

    @Test
    void fromHours를_지정해서_호출하면_해당_값으로_파이프라인이_실행된다() throws Exception {
        given(pipelineService.run(any())).willReturn(stubResult(5, 5));

        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedCount").value(5))
                .andExpect(jsonPath("$.savedCount").value(5));
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
    void 파이프라인_결과에_failedSources가_포함된다() throws Exception {
        PipelineResult resultWithFailure = PipelineResult.builder()
                .fetchedCount(10)
                .savedCount(8)
                .duplicateCount(0)
                .clustersCreated(1)
                .linkedArticleCount(2)
                .summariesGenerated(1)
                .failedSources(List.of("BBC_WORLD"))
                .failedCategories(List.of())
                .summaryFailedCount(0)
                .executedAt(LocalDateTime.of(2026, 4, 7, 0, 0))
                .build();
        given(pipelineService.run(any())).willReturn(resultWithFailure);

        mockMvc.perform(post("/api/v1/pipeline/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromHours\": 48}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedSources[0]").value("BBC_WORLD"));
    }
}
