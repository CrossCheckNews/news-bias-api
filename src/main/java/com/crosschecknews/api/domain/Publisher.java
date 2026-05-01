package com.crosschecknews.api.domain;

// Design Ref: §3 — Pragmatic Layered Architecture, Publisher entity
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "publisher")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoliticalLeaning politicalLeaning;

    @Column(unique = true)
    private String rssUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void update(String name, Country country, PoliticalLeaning politicalLeaning, String rssUrl) {
        this.name = name;
        this.country = country;
        this.politicalLeaning = politicalLeaning;
        this.rssUrl = rssUrl;
    }
}
