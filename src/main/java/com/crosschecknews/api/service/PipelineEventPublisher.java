package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.PipelineStatus;
import com.crosschecknews.api.domain.PipelineStep;
import com.crosschecknews.api.dto.dashboard.PipelineStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class PipelineEventPublisher {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE 초기 연결 확인 이벤트 전송 실패, emitter 제거: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }

    public void publish(Long pipelineRunId, PipelineStep step, PipelineStatus status, String message, int progress) {
        publish(PipelineStreamEvent.builder()
                .pipelineRunId(pipelineRunId)
                .step(step)
                .status(status)
                .message(message)
                .progress(progress)
                .emittedAt(LocalDateTime.now())
                .build());
    }

    public void publish(Long pipelineRunId, PipelineStep step, PipelineStatus status,
                        String message, int progress, String targetName, String errorMessage) {
        publish(PipelineStreamEvent.builder()
                .pipelineRunId(pipelineRunId)
                .step(step)
                .status(status)
                .message(message)
                .progress(progress)
                .targetName(targetName)
                .errorMessage(errorMessage)
                .emittedAt(LocalDateTime.now())
                .build());
    }

    public void publish(PipelineStreamEvent event) {
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("pipeline")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                log.warn("SSE 전송 실패, emitter 제거: {}", e.getMessage());
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
