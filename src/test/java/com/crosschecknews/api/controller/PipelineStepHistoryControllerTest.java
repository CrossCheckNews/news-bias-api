package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.dto.dashboard.PipelineStepHistoryResponse;
import com.crosschecknews.api.service.PipelineStepHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PipelineStepHistoryController.class)
class PipelineStepHistoryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PipelineStepHistoryService pipelineStepHistoryService;

    private PipelineStepHistoryResponse stubItem() {
        return PipelineStepHistoryResponse.builder()
                .id(1L)
                .pipelineRunId(10L)
                .step(PipelineStep.RSS_COLLECT)
                .status(PipelineStatus.SUCCESS)
                .targetType("PUBLISHER_FEED")
                .targetName("BBC")
                .processedCount(5)
                .message("RSS 피드 수집 완료")
                .startedAt(LocalDateTime.of(2026, 4, 30, 9, 0))
                .finishedAt(LocalDateTime.of(2026, 4, 30, 9, 1))
                .build();
    }

    @Test
    void 날짜_없이_전체_이력을_조회하면_페이지네이션_결과를_반환한다() throws Exception {
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(stubItem()), PageRequest.of(0, 20), 1);
        given(pipelineStepHistoryService.getHistories(isNull(), argThat(List::isEmpty), eq(0), eq(20))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].pipelineRunId").value(10))
                .andExpect(jsonPath("$.content[0].step").value("RSS_COLLECT"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void 날짜_조건으로_이력을_조회한다() throws Exception {
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(stubItem()), PageRequest.of(0, 20), 1);
        given(pipelineStepHistoryService.getHistories(any(), argThat(List::isEmpty), eq(0), eq(20))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories").param("date", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].targetName").value("BBC"));
    }

    @Test
    void 페이지_크기를_지정해서_이력을_조회한다() throws Exception {
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);
        given(pipelineStepHistoryService.getHistories(isNull(), argThat(List::isEmpty), eq(1), eq(10))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 이력이_없으면_빈_페이지를_반환한다() throws Exception {
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(pipelineStepHistoryService.getHistories(isNull(), argThat(List::isEmpty), eq(0), eq(20))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void status_FAILED_조건으로_이력을_조회한다() throws Exception {
        PipelineStepHistoryResponse failedItem = PipelineStepHistoryResponse.builder()
                .id(2L).pipelineRunId(10L)
                .step(PipelineStep.ARTICLE_SAVE).status(PipelineStatus.FAILED)
                .targetType("ARTICLE").targetName("https://example.com/article")
                .processedCount(0).errorType("MISSING_REQUIRED_FIELD")
                .errorMessage("headline이 null 또는 빈 값입니다")
                .message("필수 필드 누락으로 기사 저장 건너뜀")
                .startedAt(LocalDateTime.of(2026, 4, 30, 9, 0))
                .finishedAt(LocalDateTime.of(2026, 4, 30, 9, 0))
                .build();
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(failedItem), PageRequest.of(0, 20), 1);
        given(pipelineStepHistoryService.getHistories(isNull(), eq(List.of(PipelineStatus.FAILED)), eq(0), eq(20))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].errorType").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 날짜와_status를_함께_조회한다() throws Exception {
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(stubItem()), PageRequest.of(0, 20), 1);
        given(pipelineStepHistoryService.getHistories(any(), eq(List.of(PipelineStatus.SUCCESS)), eq(0), eq(20))).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories")
                        .param("date", "2026-04-30")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test
    void 여러_status를_함께_조회한다() throws Exception {
        PipelineStepHistoryResponse failedItem = PipelineStepHistoryResponse.builder()
                .id(2L).pipelineRunId(10L)
                .step(PipelineStep.ARTICLE_SAVE).status(PipelineStatus.FAILED)
                .targetType("ARTICLE").targetName("NORMALIZED_ARTICLES")
                .processedCount(1).failedCount(1)
                .message("기사 저장 실패")
                .startedAt(LocalDateTime.of(2026, 4, 30, 9, 0))
                .finishedAt(LocalDateTime.of(2026, 4, 30, 9, 0))
                .build();
        Page<PipelineStepHistoryResponse> page = new PageImpl<>(List.of(failedItem), PageRequest.of(0, 20), 1);
        given(pipelineStepHistoryService.getHistories(
                isNull(),
                eq(List.of(PipelineStatus.FAILED, PipelineStatus.PARTIAL_FAILED)),
                eq(0),
                eq(20))
        ).willReturn(page);

        mockMvc.perform(get("/api/v1/pipeline/histories")
                        .param("statuses", "FAILED", "PARTIAL_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }
}
