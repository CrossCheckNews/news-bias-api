package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Article;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.repository.ArticleRepository;
import com.crosschecknews.api.service.ArticleDeduplicationService.DuplicateCheckResult;
import com.crosschecknews.api.service.ArticleDeduplicationService.DuplicateReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSaveService {

    private final RssCollectService rssCollectService;
    private final ArticleNormalizationService normalizationService;
    private final ArticleDeduplicationService deduplicationService;
    private final ArticleRepository articleRepository;

    @Transactional
    public FetchAndSaveResult fetchAndSave(RssCollectRequest request) {
        List<FeedCollectResult> feedResults = (request == null || request.isCollectAll())
                ? rssCollectService.collectAll()
                : rssCollectService.collectByCodes(request.getFeedSourceCodes());

        int totalFetched = 0, totalSaved = 0, totalDuplicate = 0, totalFailed = 0;
        Map<DuplicateReason, Integer> reasonCounts = new EnumMap<>(DuplicateReason.class);
        List<FetchAndSaveResult.FeedSummary> summaries = new ArrayList<>();

        for (FeedCollectResult feedResult : feedResults) {
            if (!feedResult.isSuccess()) {
                summaries.add(failedFeedSummary(feedResult));
                totalFailed++;
                continue;
            }

            int saved = 0, duplicates = 0, failed = 0;

            for (ArticleCandidate candidate : feedResult.getArticles()) {
                try {
                    NormalizedArticleCandidate normalized = normalizationService.normalize(candidate);
                    DuplicateCheckResult dupResult = deduplicationService.check(normalized);

                    if (dupResult.isDuplicate()) {
                        duplicates++;
                        reasonCounts.merge(dupResult.reason(), 1, Integer::sum);
                        continue;
                    }

                    articleRepository.save(toEntity(normalized));
                    saved++;

                } catch (Exception e) {
                    failed++;
                    log.error("Article 저장 실패 [{}] url={} cause={}",
                            feedResult.getFeedSourceCode(), candidate.getUrl(), e.getMessage(), e);
                }
            }

            int fetched = feedResult.getCount();
            totalFetched  += fetched;
            totalSaved    += saved;
            totalDuplicate += duplicates;
            totalFailed   += failed;

            log.info("피드 저장 완료 [{}] fetched={} saved={} duplicates={} failed={}",
                    feedResult.getFeedSourceCode(), fetched, saved, duplicates, failed);

            summaries.add(FetchAndSaveResult.FeedSummary.builder()
                    .feedSourceCode(feedResult.getFeedSourceCode())
                    .publisherName(feedResult.getPublisherName())
                    .collectSuccess(true)
                    .fetched(fetched)
                    .saved(saved)
                    .duplicates(duplicates)
                    .failed(failed)
                    .build());
        }

        return FetchAndSaveResult.builder()
                .fetchedCount(totalFetched)
                .savedCount(totalSaved)
                .duplicateCount(totalDuplicate)
                .failedCount(totalFailed)
                .duplicateReasonCounts(toStringKeyMap(reasonCounts))
                .feeds(summaries)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private Article toEntity(NormalizedArticleCandidate n) {
        return Article.builder()
                .headline(n.getHeadline())
                .normalizedHeadline(n.getNormalizedHeadline())
                .url(n.getUrl())
                .normalizedUrl(n.getNormalizedUrl())
                .description(n.getDescription())
                .rssGuid(n.getRssGuid())
                .dedupeKey(n.getDedupeKey())
                .publishedAt(n.getPublishedAt())
                .publisher(n.getPublisher())
                .category(n.getCategory())
                .build();
    }

    private FetchAndSaveResult.FeedSummary failedFeedSummary(FeedCollectResult feedResult) {
        return FetchAndSaveResult.FeedSummary.builder()
                .feedSourceCode(feedResult.getFeedSourceCode())
                .publisherName(feedResult.getPublisherName())
                .collectSuccess(false)
                .fetched(0).saved(0).duplicates(0).failed(0)
                .errorMessage(feedResult.getErrorMessage())
                .build();
    }

    private Map<String, Integer> toStringKeyMap(Map<DuplicateReason, Integer> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }
}
