package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.FeedCollectResult;
import com.crosschecknews.api.dto.FetchAndSaveResult;
import com.crosschecknews.api.dto.RssCollectRequest;
import com.crosschecknews.api.service.ArticleSaveService;
import com.crosschecknews.api.service.RssCollectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Collect", description = "RSS 수집 API")
@RestController
@RequestMapping("/api/v1/collect")
@RequiredArgsConstructor
public class CollectController {

    private final RssCollectService rssCollectService;
    private final ArticleSaveService articleSaveService;

    @Operation(
            summary = "RSS 수집 미리보기 (DB 저장 없음)",
            description = "FeedSource RSS를 수집해 ArticleCandidate 목록을 반환합니다. " +
                          "feedSourceCodes 생략 시 전체 수집. 예: [\"FOX_WORLD\", \"NYT_WORLD\"]"
    )
    @PostMapping("/preview")
    public List<FeedCollectResult> preview(@RequestBody(required = false) RssCollectRequest request) {
        if (request == null || request.isCollectAll()) {
            return rssCollectService.collectAll();
        }
        return rssCollectService.collectByCodes(request.getFeedSourceCodes());
    }

    @Operation(
            summary = "RSS 수집 후 DB 저장",
            description = "RSS 수집 → 정규화(HTML 제거, URL 정규화) → 중복 제거 → Article 저장. " +
                          "feedSourceCodes 생략 시 전체 수집. " +
                          "응답에 피드별 fetched/saved/skipped 포함."
    )
    @PostMapping("/fetch-and-save")
    public FetchAndSaveResult fetchAndSave(@RequestBody(required = false) RssCollectRequest request) {
        return articleSaveService.fetchAndSave(request);
    }
}
