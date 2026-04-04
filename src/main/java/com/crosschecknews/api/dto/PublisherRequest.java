package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
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
    private String name;

    @NotNull
    private Country country;

    @NotNull
    private PoliticalLeaning politicalLeaning;

    @NotBlank
    private String rssUrl;
}
