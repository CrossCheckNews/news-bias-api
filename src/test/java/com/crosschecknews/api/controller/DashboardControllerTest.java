package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.dashboard.DashboardChartsResponse;
import com.crosschecknews.api.dto.dashboard.DashboardSummaryResponse;
import com.crosschecknews.api.service.DashboardService;
import com.crosschecknews.api.service.PipelineEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean DashboardService dashboardService;
    @MockitoBean PipelineEventPublisher eventPublisher;

    @Test
    void 대시보드_요약은_200과_집계_필드를_반환한다() throws Exception {
        given(dashboardService.getSummary(isNull())).willReturn(DashboardSummaryResponse.builder()
                .totalArticles(20)
                .totalTopics(5)
                .todayCollectedArticles(8)
                .failedJobs(1)
                .lastCollectedAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .recentRuns(List.of())
                .build());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles").value(20))
                .andExpect(jsonPath("$.totalTopics").value(5))
                .andExpect(jsonPath("$.todayCollectedArticles").value(8))
                .andExpect(jsonPath("$.failedJobs").value(1))
                .andExpect(jsonPath("$.lastCollectedAt").isNotEmpty())
                .andExpect(jsonPath("$.recentRuns").isArray());
    }

    @Test
    void 대시보드_요약은_date_파라미터를_서비스에_전달한다() throws Exception {
        given(dashboardService.getSummary(LocalDate.of(2026, 5, 1))).willReturn(DashboardSummaryResponse.builder()
                .totalArticles(20)
                .totalTopics(5)
                .todayCollectedArticles(3)
                .successJobs(2)
                .failedJobs(1)
                .recentRuns(List.of())
                .build());

        mockMvc.perform(get("/api/dashboard/summary").param("date", "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCollectedArticles").value(3));
    }

    @Test
    void 차트_데이터는_200과_배열_필드를_반환한다() throws Exception {
        given(dashboardService.getCharts(isNull())).willReturn(DashboardChartsResponse.builder()
                .articlesByPublisher(List.of(
                        DashboardChartsResponse.NamedCount.builder().name("Fox News").count(4).build(),
                        DashboardChartsResponse.NamedCount.builder().name("NYT").count(3).build()
                ))
                .topicsByCountry(List.of(
                        DashboardChartsResponse.NamedCount.builder().name("US").count(2).build()
                ))
                .pipelineStatusCounts(List.of(
                        DashboardChartsResponse.NamedCount.builder().name("SUCCESS").count(3).build(),
                        DashboardChartsResponse.NamedCount.builder().name("FAILED").count(1).build()
                ))
                .build());

        mockMvc.perform(get("/api/dashboard/charts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articlesByPublisher").isArray())
                .andExpect(jsonPath("$.articlesByPublisher.length()").value(2))
                .andExpect(jsonPath("$.articlesByPublisher[0].name").value("Fox News"))
                .andExpect(jsonPath("$.articlesByPublisher[0].count").value(4))
                .andExpect(jsonPath("$.topicsByCountry").isArray())
                .andExpect(jsonPath("$.pipelineStatusCounts").isArray());
    }

    @Test
    void 차트_데이터는_date_파라미터를_서비스에_전달한다() throws Exception {
        given(dashboardService.getCharts(LocalDate.of(2026, 5, 1))).willReturn(DashboardChartsResponse.builder()
                .articlesByPublisher(List.of())
                .topicsByCountry(List.of())
                .pipelineStatusCounts(List.of(
                        DashboardChartsResponse.NamedCount.builder().name("SUCCESS").count(2).build()
                ))
                .build());

        mockMvc.perform(get("/api/dashboard/charts").param("date", "2026-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineStatusCounts[0].count").value(2));
    }

    @Test
    void SSE_스트림은_text_event_stream_콘텐츠_타입으로_응답한다() throws Exception {
        doAnswer(inv -> {
            ((SseEmitter) inv.getArgument(0)).complete();
            return null;
        }).when(eventPublisher).register(any(SseEmitter.class));

        MvcResult result = mockMvc.perform(get("/api/pipeline/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.containsString(MediaType.TEXT_EVENT_STREAM_VALUE)));
    }

    @Test
    void SSE_스트림_호출_시_eventPublisher에_emitter가_등록된다() throws Exception {
        doAnswer(inv -> {
            ((SseEmitter) inv.getArgument(0)).complete();
            return null;
        }).when(eventPublisher).register(any(SseEmitter.class));

        MvcResult result = mockMvc.perform(get("/api/pipeline/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        verify(eventPublisher).register(any(SseEmitter.class));
    }
}
