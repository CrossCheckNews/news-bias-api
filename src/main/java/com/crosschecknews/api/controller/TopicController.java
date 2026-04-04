package com.crosschecknews.api.controller;

import com.crosschecknews.api.domain.TopicStatus;
import com.crosschecknews.api.dto.*;
import com.crosschecknews.api.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@Valid @RequestBody TopicRequest request) {
        return topicService.create(request);
    }

    @GetMapping
    public Page<TopicResponse> findAll(
            @RequestParam(required = false) TopicStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return topicService.findAll(status, pageable);
    }

    @GetMapping("/{id}")
    public TopicDetailResponse findById(@PathVariable Long id) {
        return topicService.findById(id);
    }

    @PutMapping("/{id}")
    public TopicResponse update(@PathVariable Long id, @Valid @RequestBody TopicRequest request) {
        return topicService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        topicService.delete(id);
    }

    // 비교 뷰: GET /api/v1/topics/{id}/articles?groupBy=leaning
    @GetMapping("/{id}/articles")
    public TopicComparisonResponse getComparisonView(
            @PathVariable Long id,
            @RequestParam(defaultValue = "leaning") String groupBy
    ) {
        return topicService.getComparisonView(id, groupBy);
    }

    // 기사 연결
    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void linkArticle(@PathVariable Long id, @Valid @RequestBody TopicArticleLinkRequest request) {
        topicService.linkArticle(id, request.getArticleId());
    }

    // 기사 연결 해제
    @DeleteMapping("/{id}/articles/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkArticle(@PathVariable Long id, @PathVariable Long articleId) {
        topicService.unlinkArticle(id, articleId);
    }
}
