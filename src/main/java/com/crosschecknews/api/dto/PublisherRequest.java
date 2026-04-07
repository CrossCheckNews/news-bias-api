package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PublisherRequest {

    @NotBlank
    @Schema(description = "언론사 이름", example = "The New York Times")
    private String name;

    @NotNull
    @Schema(description = "국가 코드 (KR / US / JP / CN / GB / DE / FR / AU)", example = "US")
    private Country country;

    @NotNull
    @Schema(description = "정치성향 (CONSERVATIVE / PROGRESSIVE)", example = "PROGRESSIVE")
    private PoliticalLeaning politicalLeaning;
}
