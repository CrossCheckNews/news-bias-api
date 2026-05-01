package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {
    java.util.Optional<Publisher> findByName(String name);
}
