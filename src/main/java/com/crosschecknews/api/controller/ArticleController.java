package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.ArticleFetchRequest;
import com.crosschecknews.api.dto.ArticleRequest;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@Valid @RequestBody ArticleRequest request) {
        return articleService.create(request);
    }

    @GetMapping
    public Page<ArticleResponse> findAll(
            @RequestParam(required = false) Long publisherId,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return articleService.findAll(publisherId, pageable);
    }

    @GetMapping("/{id}")
    public ArticleResponse findById(@PathVariable Long id) {
        return articleService.findById(id);
    }

    // RSS 수집 트리거: POST /api/v1/articles/fetch { "publisherId": 1 }
    @PostMapping("/fetch")
    public ArticleService.FetchResult fetch(@Valid @RequestBody ArticleFetchRequest request) {
        return articleService.fetchFromRss(request.getPublisherId());
    }
}
