package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.PublisherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private PublisherRepository publisherRepository;

    @InjectMocks
    private PublisherService publisherService;

    private Publisher buildPublisher(Long id) {
        return Publisher.builder()
                .id(id)
                .name("New York Times")
                .country(Country.GB)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    @Test
    void 언론사_목록_조회() {
        given(publisherRepository.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(buildPublisher(1L), buildPublisher(2L))));

        Page<PublisherResponse> result = publisherService.findAll(0, 20);

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void 언론사_단건_조회_성공() {
        given(publisherRepository.findById(1L)).willReturn(Optional.of(buildPublisher(1L)));

        PublisherResponse response = publisherService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_언론사_조회_실패() {
        given(publisherRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publisherService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void 언론사_삭제_성공() {
        Publisher publisher = buildPublisher(1L);
        given(publisherRepository.findById(1L)).willReturn(Optional.of(publisher));

        publisherService.delete(1L);

        verify(publisherRepository).delete(publisher);
    }

    @Test
    void 존재하지_않는_언론사_삭제_실패() {
        given(publisherRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publisherService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
