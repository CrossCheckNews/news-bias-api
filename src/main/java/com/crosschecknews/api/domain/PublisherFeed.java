package com.crosschecknews.api.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "publisher_feed")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String rssUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
