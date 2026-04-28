package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.FetchAndSaveResult;
import com.crosschecknews.api.dto.SummarizeResponse;
import com.crosschecknews.api.dto.TestPipelineResult;
import com.crosschecknews.api.service.TestPipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TestPipelineController.class)
class TestPipelineControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TestPipelineService testPipelineService;

    private TestPipelineResult stubResult(int fetched, int saved, int topics, int summaries) {
        FetchAndSaveResult fetchAndSave = FetchAndSaveResult.builder()
                .fetchedCount(fetched)
                .savedCount(saved)
                .duplicateCount(0)
                .failedCount(0)
                .feeds(List.of())
                .executedAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .build();

        List<TestPipelineResult.TopicResult> topicList = java.util.Collections.nCopies(
                topics,
                TestPipelineResult.TopicResult.builder()
                        .topicId(1L).title("Test Topic").articles(List.of())
                        .build()
        );

        List<SummarizeResponse> summaryList = java.util.Collections.nCopies(
                summaries,
                SummarizeResponse.builder()
                        .topicId(1L).title("Test Topic").summary("Test summary")
                        .aiModel("gemini").generatedAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                        .build()
        );

        return TestPipelineResult.builder()
                .fetchAndSave(fetchAndSave)
                .topics(topicList)
                .summaries(summaryList)
                .executedAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .build();
    }

    @Test
    void demo_load_호출_시_200_OK와_결과를_반환한다() throws Exception {
        given(testPipelineService.loadFromDemoData()).willReturn(stubResult(30, 28, 3, 3));

        mockMvc.perform(post("/api/v1/test/pipeline/demo-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchAndSave.fetchedCount").value(30))
                .andExpect(jsonPath("$.fetchAndSave.savedCount").value(28))
                .andExpect(jsonPath("$.topics.length()").value(3))
                .andExpect(jsonPath("$.summaries.length()").value(3));

        verify(testPipelineService).loadFromDemoData();
    }

    @Test
    void demo_load_응답에_executedAt이_포함된다() throws Exception {
        given(testPipelineService.loadFromDemoData()).willReturn(stubResult(10, 10, 1, 1));

        mockMvc.perform(post("/api/v1/test/pipeline/demo-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedAt").exists());
    }

    @Test
    void demo_load_기사가_없어도_200_OK를_반환한다() throws Exception {
        given(testPipelineService.loadFromDemoData()).willReturn(stubResult(0, 0, 0, 0));

        mockMvc.perform(post("/api/v1/test/pipeline/demo-load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchAndSave.fetchedCount").value(0))
                .andExpect(jsonPath("$.topics.length()").value(0))
                .andExpect(jsonPath("$.summaries.length()").value(0));
    }
}
