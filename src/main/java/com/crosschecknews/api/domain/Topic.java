package com.crosschecknews.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "topic")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // AI-generated fields
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private LocalDateTime summaryGeneratedAt;

    private String aiModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleCategory articleCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status;

    private LocalDate startDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void update(String title, String summary, TopicStatus status, LocalDate startDate) {
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.startDate = startDate;
    }

    public void applySummary(String aiSummary, String aiModel) {
        this.aiSummary = aiSummary;
        this.aiModel = aiModel;
        this.summaryGeneratedAt = LocalDateTime.now();
    }
}
