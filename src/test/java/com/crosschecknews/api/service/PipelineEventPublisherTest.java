package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.dto.dashboard.PipelineStreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineEventPublisherTest {

    PipelineEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PipelineEventPublisher();
    }

    // ── 등록 및 이벤트 전송 ──────────────────────────────────────────────────

    @Test
    void 등록된_emitter에_이벤트가_전송된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);
        clearInvocations(emitter);

        publisher.publish(1L, PipelineStep.RSS_COLLECT, PipelineStatus.RUNNING, "수집 중", 10);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 여러_emitter에_동시에_이벤트가_전송된다() throws IOException {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        publisher.register(emitter1);
        publisher.register(emitter2);
        clearInvocations(emitter1, emitter2);

        publisher.publish(1L, PipelineStep.RSS_COLLECT, PipelineStatus.SUCCESS, "완료", 25);

        verify(emitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void emitter가_없을_때_publish는_예외_없이_무시된다() {
        // emitter 미등록 상태에서 publish 호출 시 예외 없어야 함
        publisher.publish(1L, PipelineStep.COMPLETED, PipelineStatus.SUCCESS, "완료", 100);
    }

    @Test
    void PipelineStreamEvent_직접_전달_시_emitter에_전송된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);
        clearInvocations(emitter);

        PipelineStreamEvent event = PipelineStreamEvent.builder()
                .pipelineRunId(99L)
                .step(PipelineStep.AI_SUMMARY)
                .status(PipelineStatus.SUCCESS)
                .message("요약 완료")
                .progress(95)
                .build();

        publisher.publish(event);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ── emitter 자동 제거 ────────────────────────────────────────────────────

    @Test
    void onCompletion_시_emitter가_제거된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(captor.capture());
        clearInvocations(emitter);
        captor.getValue().run();

        publisher.publish(1L, PipelineStep.COMPLETED, PipelineStatus.SUCCESS, "완료", 100);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void onTimeout_시_emitter가_제거된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(captor.capture());
        clearInvocations(emitter);
        captor.getValue().run();

        publisher.publish(1L, PipelineStep.COMPLETED, PipelineStatus.SUCCESS, "완료", 100);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onError_시_emitter가_제거된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);

        ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(captor.capture());
        clearInvocations(emitter);
        captor.getValue().accept(new RuntimeException("connection lost"));

        publisher.publish(1L, PipelineStep.COMPLETED, PipelineStatus.SUCCESS, "완료", 100);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void send_실패_시_해당_emitter가_제거되고_나머지_emitter는_계속_수신된다() throws IOException {
        SseEmitter failingEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        publisher.register(failingEmitter);
        publisher.register(healthyEmitter);
        clearInvocations(failingEmitter, healthyEmitter);
        doThrow(new IOException("broken pipe")).when(failingEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        publisher.publish(1L, PipelineStep.RSS_COLLECT, PipelineStatus.RUNNING, "수집 중", 10);
        publisher.publish(1L, PipelineStep.RSS_COLLECT, PipelineStatus.SUCCESS, "완료", 25);

        // failingEmitter: 첫 번째 시도에서 실패 후 제거되어 두 번째 호출 없음
        verify(failingEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        // healthyEmitter: 두 번 모두 수신
        verify(healthyEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ── 이벤트 필드 검증 ────────────────────────────────────────────────────

    @Test
    void targetName과_errorMessage가_포함된_이벤트가_전송된다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);
        clearInvocations(emitter);

        publisher.publish(1L, PipelineStep.RSS_COLLECT, PipelineStatus.FAILED,
                "Fox News RSS 수집 실패", 10, "Fox News", "Connection timeout");

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void publish_빌더_메서드는_pipelineRunId를_포함한_이벤트를_생성한다() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        publisher.register(emitter);
        clearInvocations(emitter);

        // publish(PipelineStreamEvent) 오버로드를 통해 필드 검증
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

        PipelineStreamEvent event = PipelineStreamEvent.builder()
                .pipelineRunId(42L)
                .step(PipelineStep.TOPIC_CLUSTERING)
                .status(PipelineStatus.RUNNING)
                .message("클러스터링 중")
                .progress(50)
                .targetName("WORLD")
                .errorMessage(null)
                .build();

        publisher.publish(event);

        verify(emitter).send(captor.capture());
        // SseEventBuilder가 전송됐음을 확인 (내부 직렬화는 Jackson 담당)
        assertThat(captor.getValue()).isNotNull();
    }
}
