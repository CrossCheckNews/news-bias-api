package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Page<Topic> findByStatus(TopicStatus status, Pageable pageable);
}
