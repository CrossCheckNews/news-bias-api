package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.service.PublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.crosschecknews.api.dto.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Publisher", description = "언론사 관리 API")
@RestController
@RequestMapping("/api/v1/publishers")
@RequiredArgsConstructor
public class PublisherController {

    private final PublisherService publisherService;

    @Operation(
            summary = "언론사 목록 조회",
            description = "등록된 언론사 목록을 페이지 단위로 반환합니다. 정렬은 name ASC로 고정됩니다.",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호. 0부터 시작", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
                    @Parameter(name = "size", description = "페이지 크기", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20", minimum = "1"))
            }
    )
    @GetMapping
    public PageResponse<PublisherResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(publisherService.findAll(page, size));
    }

    @Operation(summary = "언론사 단건 조회", description = "ID로 특정 언론사 정보를 조회합니다.")
    @GetMapping("/{id}")
    public PublisherResponse findById(@PathVariable Long id) {
        return publisherService.findById(id);
    }

    @Operation(summary = "언론사 삭제", description = "언론사를 삭제합니다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        publisherService.delete(id);
    }
}
