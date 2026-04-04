package com.crosschecknews.api.service;

// Design Ref: §6.1 — PublisherService CRUD, @Transactional(readOnly=true) default
// Plan SC: FR-01, FR-02, FR-03
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.PublisherRequest;
import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublisherService {

    private final PublisherRepository publisherRepository;

    @Transactional
    public PublisherResponse create(PublisherRequest request) {
        Publisher publisher = Publisher.builder()
                .name(request.getName())
                .country(request.getCountry())
                .politicalLeaning(request.getPoliticalLeaning())
                .rssUrl(request.getRssUrl())
                .build();
        return PublisherResponse.from(publisherRepository.save(publisher));
    }

    public List<PublisherResponse> findAll() {
        return publisherRepository.findAll().stream()
                .map(PublisherResponse::from)
                .toList();
    }

    public PublisherResponse findById(Long id) {
        return PublisherResponse.from(getPublisher(id));
    }

    @Transactional
    public PublisherResponse update(Long id, PublisherRequest request) {
        Publisher publisher = getPublisher(id);
        publisher.update(
                request.getName(),
                request.getCountry(),
                request.getPoliticalLeaning(),
                request.getRssUrl()
        );
        return PublisherResponse.from(publisher);
    }

    @Transactional
    public void delete(Long id) {
        Publisher publisher = getPublisher(id);
        publisherRepository.delete(publisher);
    }

    Publisher getPublisher(Long id) {
        return publisherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publisher not found: " + id));
    }
}
