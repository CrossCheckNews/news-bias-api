package com.crosschecknews.api.exception;

import com.crosschecknews.api.client.GeminiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** 중복 URL/dedupeKey 등 unique constraint 위반 시 409 반환 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT,
                "Duplicate resource: " + ex.getMostSpecificCause().getMessage());
    }

    /** RSS 파싱 실패 */
    @ExceptionHandler(RssFetchException.class)
    public ResponseEntity<ErrorResponse> handleRssFetch(RssFetchException ex) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /** Gemini AI 호출 실패 */
    @ExceptionHandler(GeminiException.class)
    public ResponseEntity<ErrorResponse> handleGemini(GeminiException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("429")) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Gemini API rate limit exceeded. Please wait and retry.");
        }
        return error(HttpStatus.BAD_GATEWAY, "AI service error: " + ex.getMessage());
    }

    /** 요약 대상 기사 없음 등 비즈니스 조건 미충족 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
