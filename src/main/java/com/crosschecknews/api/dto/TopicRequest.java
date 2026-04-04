package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.TopicStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TopicRequest {

    @NotBlank
    private String title;

    private String summary;

    @NotNull
    private TopicStatus status;

    private LocalDate startDate;
}
