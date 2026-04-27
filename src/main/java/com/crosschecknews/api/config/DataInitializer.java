package com.crosschecknews.api.config;

import com.crosschecknews.api.domain.FeedSource;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.domain.PublisherFeed;
import com.crosschecknews.api.repository.PublisherFeedRepository;
import com.crosschecknews.api.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final PublisherRepository publisherRepository;
    private final PublisherFeedRepository publisherFeedRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (FeedSource source : FeedSource.values()) {
            // Publisher — 이름 기준으로 중복 방지
            Publisher publisher = publisherRepository.findByName(source.getPublisherName())
                    .orElseGet(() -> {
                        Publisher newPublisher = Publisher.builder()
                                .name(source.getPublisherName())
                                .country(source.getCountry())
                                .politicalLeaning(source.getPoliticalLeaning())
                                .build();
                        log.info("Seeded publisher: {}", source.getPublisherName());
                        return publisherRepository.save(newPublisher);
                    });

            // PublisherFeed — 동일 publisher + category 조합 중복 방지
            if (!publisherFeedRepository.existsByPublisherIdAndCategory(publisher.getId(), source.getArticleCategory())) {
                publisherFeedRepository.save(PublisherFeed.builder()
                        .publisher(publisher)
                        .articleCategory(source.getArticleCategory())
                        .rssUrl(source.getRssUrl())
                        .build());
                log.info("Seeded feed: {} / {}", source.getPublisherName(), source.getArticleCategory());
            }
        }
    }
}
