package com.crosschecknews.api.service;

import com.crosschecknews.api.dto.NormalizedArticleCandidate;
import com.crosschecknews.api.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleDeduplicationService {

    private final ArticleRepository articleRepository;

    /**
     * 우선순위 기반 중복 판별.
     *
     * Priority 1 — rssGuid + publisherId
     *   RSS 표준 식별자. 신뢰도 가장 높음.
     *   단, guid는 publisher 내에서만 고유하므로 publisher와 함께 비교.
     *
     * Priority 2 — normalizedUrl
     *   추적 파라미터 제거 + trailing slash 정리 + https 통일 후 비교.
     *   guid가 없거나 변경됐을 때 사용.
     *
     * Priority 3 — dedupeKey
     *   SHA-256(publisherName|normalizedHeadline|publishedDate).
     *   URL이 CDN 경로 변경 등으로 달라진 경우 fallback.
     */
    public DuplicateCheckResult check(NormalizedArticleCandidate candidate) {
        Long publisherId = candidate.getPublisher().getId();

        // Priority 1
        if (hasRssGuid(candidate.getRssGuid())) {
            if (articleRepository.existsByPublisher_IdAndRssGuid(publisherId, candidate.getRssGuid())) {
                log.debug("중복 감지 [rssGuid] publisher={} guid={}", publisherId, candidate.getRssGuid());
                return DuplicateCheckResult.duplicate(DuplicateReason.RSS_GUID);
            }
        }

        // Priority 2
        if (articleRepository.existsByNormalizedUrl(candidate.getNormalizedUrl())) {
            log.debug("중복 감지 [normalizedUrl] url={}", candidate.getNormalizedUrl());
            return DuplicateCheckResult.duplicate(DuplicateReason.NORMALIZED_URL);
        }

        // Priority 3
        if (articleRepository.existsByDedupeKey(candidate.getDedupeKey())) {
            log.debug("중복 감지 [dedupeKey] key={}", candidate.getDedupeKey());
            return DuplicateCheckResult.duplicate(DuplicateReason.DEDUPE_KEY);
        }

        return DuplicateCheckResult.notDuplicate();
    }

    private boolean hasRssGuid(String guid) {
        return guid != null && !guid.isBlank();
    }

    // ── 결과 타입 ─────────────────────────────────────────────────────────────

    public record DuplicateCheckResult(boolean isDuplicate, DuplicateReason reason) {
        static DuplicateCheckResult duplicate(DuplicateReason reason) {
            return new DuplicateCheckResult(true, reason);
        }
        static DuplicateCheckResult notDuplicate() {
            return new DuplicateCheckResult(false, null);
        }
    }

    public enum DuplicateReason {
        RSS_GUID,       // Priority 1
        NORMALIZED_URL, // Priority 2
        DEDUPE_KEY      // Priority 3
    }
}
