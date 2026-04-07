package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Country;
import com.crosschecknews.api.domain.PoliticalLeaning;
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.PublisherRequest;
import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.PublisherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
                .name("BBC News")
                .country(Country.GB)
                .politicalLeaning(PoliticalLeaning.CONSERVATIVE)
                .build();
    }

    private PublisherRequest buildRequest() {
        return new PublisherRequest("BBC News", Country.GB, PoliticalLeaning.CONSERVATIVE);
    }

    @Test
    void 언론사_등록_성공() {
        Publisher saved = buildPublisher(1L);
        given(publisherRepository.save(any(Publisher.class))).willReturn(saved);

        PublisherResponse response = publisherService.create(buildRequest());

        assertThat(response.getName()).isEqualTo("BBC News");
        assertThat(response.getCountry()).isEqualTo(Country.GB);
        assertThat(response.getPoliticalLeaning()).isEqualTo(PoliticalLeaning.CONSERVATIVE);
        verify(publisherRepository).save(any(Publisher.class));
    }

    @Test
    void 언론사_목록_조회() {
        given(publisherRepository.findAll()).willReturn(List.of(buildPublisher(1L), buildPublisher(2L)));

        List<PublisherResponse> result = publisherService.findAll();

        assertThat(result).hasSize(2);
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
    void 언론사_수정_성공() {
        Publisher publisher = buildPublisher(1L);
        given(publisherRepository.findById(1L)).willReturn(Optional.of(publisher));

        PublisherRequest updateRequest = new PublisherRequest("BBC Updated", Country.GB, PoliticalLeaning.PROGRESSIVE);
        PublisherResponse response = publisherService.update(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("BBC Updated");
        assertThat(response.getPoliticalLeaning()).isEqualTo(PoliticalLeaning.PROGRESSIVE);
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
