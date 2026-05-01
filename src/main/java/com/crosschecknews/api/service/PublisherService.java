package com.crosschecknews.api.service;

// Design Ref: §6.1 — PublisherService CRUD, @Transactional(readOnly=true) default
// Plan SC: FR-01, FR-02, FR-03
import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.PublisherResponse;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.PublisherRepository;
import com.crosschecknews.api.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublisherService {

    private final PublisherRepository publisherRepository;

    public Page<PublisherResponse> findAll(int page, int size) {
        var pageable = PageUtil.of(page, size, Sort.Order.asc("name"));
        return publisherRepository.findAll(pageable).map(PublisherResponse::from);
    }

    public PublisherResponse findById(Long id) {
        return PublisherResponse.from(getPublisher(id));
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
