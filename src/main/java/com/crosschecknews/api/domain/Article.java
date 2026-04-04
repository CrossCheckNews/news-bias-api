package com.crosschecknews.api.domain;

// Design Ref: §3 — Article stores headline + url only (no body), ManyToOne Publisher
// Plan SC: FR-07 — URL unique constraint prevents duplicate storage
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "article",
        uniqueConstraints = @UniqueConstraint(columnNames = "url"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String headline;

    @Column(nullable = false, unique = true)
    private String url;

    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onFetch() {
        this.fetchedAt = LocalDateTime.now();
    }
}
