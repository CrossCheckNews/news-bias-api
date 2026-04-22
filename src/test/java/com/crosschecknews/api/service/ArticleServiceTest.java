package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.ArticleResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleService articleService;

    private Publisher buildPublisher() {
        return Publisher.builder()
                .id(1L).name("Fox News")
                .country(Country.US)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    private Article buildArticle(Publisher publisher) {
        return Article.builder()
                .id(1L).headline("Test Headline")
                .url("https://foxnews.com/1")
                .publishedAt(LocalDateTime.now())
                .publisher(publisher)
                .build();
    }

    @Test
    void 기사_단건_조회_성공() {
        Publisher publisher = buildPublisher();
        given(articleRepository.findById(1L)).willReturn(Optional.of(buildArticle(publisher)));

        ArticleResponse response = articleService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_기사_조회_실패() {
        given(articleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
