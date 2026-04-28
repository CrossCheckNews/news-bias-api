package com.crosschecknews.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_run")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStatus status;

    @Column(nullable = false)
    private int fetchedCount;

    @Column(nullable = false)
    private int savedCount;

    @Column(nullable = false)
    private int clusteredCount;

    @Column(nullable = false)
    private int summarizedCount;

    private String message;

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public void complete(PipelineStatus status, int fetchedCount, int savedCount,
                         int clusteredCount, int summarizedCount,
                         String message, String errorMessage, LocalDateTime finishedAt) {
        this.status = status;
        this.fetchedCount = fetchedCount;
        this.savedCount = savedCount;
        this.clusteredCount = clusteredCount;
        this.summarizedCount = summarizedCount;
        this.message = message;
        this.errorMessage = errorMessage;
        this.finishedAt = finishedAt;
    }
}
