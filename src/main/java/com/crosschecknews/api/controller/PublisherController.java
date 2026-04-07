package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.PublisherRequest;
import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.service.PublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Publisher", description = "언론사 관리 API")
@RestController
@RequestMapping("/api/v1/publishers")
@RequiredArgsConstructor
public class PublisherController {

    private final PublisherService publisherService;

    @Operation(summary = "언론사 등록", description = "새로운 언론사를 등록합니다. 이름, 국가, 정치성향, RSS URL을 입력합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublisherResponse create(@Valid @RequestBody PublisherRequest request) {
        return publisherService.create(request);
    }

    @Operation(summary = "언론사 목록 조회", description = "등록된 모든 언론사 목록을 반환합니다.")
    @GetMapping
    public List<PublisherResponse> findAll() {
        return publisherService.findAll();
    }

    @Operation(summary = "언론사 단건 조회", description = "ID로 특정 언론사 정보를 조회합니다.")
    @GetMapping("/{id}")
    public PublisherResponse findById(@PathVariable Long id) {
        return publisherService.findById(id);
    }

    @Operation(summary = "언론사 수정", description = "언론사 정보(이름, 국가, 정치성향, RSS URL)를 수정합니다.")
    @PutMapping("/{id}")
    public PublisherResponse update(@PathVariable Long id, @Valid @RequestBody PublisherRequest request) {
        return publisherService.update(id, request);
    }

    @Operation(summary = "언론사 삭제", description = "언론사를 삭제합니다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        publisherService.delete(id);
    }
}
