package com.crosschecknews.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "topic_article",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "article_id"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onLink() {
        this.linkedAt = LocalDateTime.now();
    }
}
