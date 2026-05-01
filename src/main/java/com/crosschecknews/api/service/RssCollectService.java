package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.FeedSource;
import com.crosschecknews.api.dto.ArticleCandidate;
import com.crosschecknews.api.dto.FeedCollectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssCollectService {

    private final RssFetchService rssFetchService;

    public List<FeedCollectResult> collectAll() {
        log.info("전체 FeedSource 수집 시작: {} 개", FeedSource.values().length);
        return Arrays.stream(FeedSource.values())
                .map(this::collectOne)
                .toList();
    }

    public List<FeedCollectResult> collectByCodes(List<String> codes) {
        log.info("선택 FeedSource 수집 시작: {}", codes);
        return codes.stream()
                .map(code -> {
                    try {
                        return FeedSource.valueOf(code);
                    } catch (IllegalArgumentException e) {
                        log.warn("알 수 없는 feedSourceCode: {}", code);
                        return null;
                    }
                })
                .filter(source -> source != null)
                .map(this::collectOne)
                .toList();
    }

    private FeedCollectResult collectOne(FeedSource source) {
        log.info("RSS 수집 시작 [{}] url={}", source.name(), source.getRssUrl());
        try {
            List<ArticleCandidate> candidates = rssFetchService.fetchWithCode(source.name(), source.getRssUrl())
                    .stream()
                    .map(entry -> ArticleCandidate.of(source, entry))
                    .toList();

            log.info("RSS 수집 완료 [{}] count={}", source.name(), candidates.size());
            return FeedCollectResult.success(source, candidates);

        } catch (Exception e) {
            // 한 피드 실패가 전체를 죽이지 않도록 격리
            log.error("RSS 수집 실패 [{}] url={} cause={}", source.name(), source.getRssUrl(), e.getMessage(), e);
            return FeedCollectResult.failure(source, classifyRssError(e), e.getMessage());
        }
    }

    private String classifyRssError(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return "RSS_TIMEOUT";
            }
            String causeMessage = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
            if (causeMessage.contains("403") || causeMessage.contains("forbidden")) {
                return "RSS_FORBIDDEN";
            }
            if (causeMessage.contains("parse") || causeMessage.contains("xml")) {
                return "RSS_PARSE_ERROR";
            }
            cause = cause.getCause();
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "RSS_TIMEOUT";
        }
        if (message.contains("403") || message.contains("forbidden")) {
            return "RSS_FORBIDDEN";
        }
        if (message.contains("parse") || message.contains("xml")) {
            return "RSS_PARSE_ERROR";
        }
        return "RSS_FEED_ERROR";
    }
}
