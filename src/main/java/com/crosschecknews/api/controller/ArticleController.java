package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.crosschecknews.api.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
            description = """
                기사 목록을 페이지 단위로 조회합니다.
                headline 검색어와 publishedAt 기준 날짜 필터를 선택적으로 사용할 수 있습니다.
                파라미터를 전달하지 않으면 전체 기사 목록을 조회합니다.
                정렬은 publishedAt DESC로 고정됩니다.
                """,
            parameters = {
                    @Parameter(
                            name = "headline",
                            description = "헤드라인 검색어. 입력하지 않으면 전체 조회",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "Trump")
                    ),
                    @Parameter(
                            name = "date",
                            description = "조회 날짜. publishedAt 기준이며 형식은 yyyy-MM-dd",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", format = "date", example = "2026-05-01")
                    ),
                    @Parameter(
                            name = "page",
                            description = "페이지 번호. 0부터 시작",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")
                    ),
                    @Parameter(
                            name = "size",
                            description = "페이지 크기",
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "integer", defaultValue = "20", minimum = "1")
                    )
            }
    )
    @GetMapping
    public PageResponse<ArticleResponse> findAll(
            @RequestParam(required = false) String headline,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(articleService.findAll(headline, date, page, size));
    }

    @Operation(summary = "기사 단건 조회", description = "ID로 특정 기사를 조회합니다.")
    @GetMapping("/{id}")
    public ArticleResponse findById(@PathVariable Long id) {
        return articleService.findById(id);
    }
}
