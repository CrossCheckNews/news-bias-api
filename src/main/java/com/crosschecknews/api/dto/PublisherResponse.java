package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.domain.Publisher;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PublisherResponse {

    private Long id;
    private String name;
    private Country country;
    private PoliticalLeaning politicalLeaning;
    private LocalDateTime createdAt;

    public static PublisherResponse from(Publisher publisher) {
        return PublisherResponse.builder()
                .id(publisher.getId())
                .name(publisher.getName())
                .country(publisher.getCountry())
                .politicalLeaning(publisher.getPoliticalLeaning())
                .createdAt(publisher.getCreatedAt())
                .build();
    }
}
