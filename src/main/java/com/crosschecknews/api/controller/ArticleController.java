package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * 기사 조회 API.
 *
 * <p>기사 수집/저장은 {@code POST /api/v1/collect/fetch-and-save} 또는
 * {@code POST /api/v1/pipeline/collect}을 통해 이루어집니다.
 */
@Tag(name = "Article", description = "기사 조회 API")
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @Operation(
            summary = "기사 목록 조회",
            description = "기사 목록을 반환합니다. publisherId로 필터링하거나 페이지네이션을 사용할 수 있습니다.",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0")),
                    @Parameter(name = "size", description = "페이지 크기", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20")),
                    @Parameter(name = "sort", description = "정렬 (예: publishedAt,desc)", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "publishedAt,desc"))
            }
    )
    @GetMapping
    public Page<ArticleResponse> findAll(
            @Parameter(description = "언론사 ID 필터. 생략 시 전체 조회.")
            @RequestParam(required = false) Long publisherId,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return articleService.findAll(publisherId, pageable);
    }

    @Operation(summary = "기사 단건 조회", description = "ID로 특정 기사를 조회합니다.")
    @GetMapping("/{id}")
    public ArticleResponse findById(@PathVariable Long id) {
        return articleService.findById(id);
    }
}
