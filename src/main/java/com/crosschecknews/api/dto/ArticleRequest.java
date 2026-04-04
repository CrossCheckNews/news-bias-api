package com.crosschecknews.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest {

    @NotBlank
    private String headline;

    @NotBlank
    private String url;

    private LocalDateTime publishedAt;

    @NotNull
    private Long publisherId;
}
