package com.crosschecknews.api.repository;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Page<Topic> findByStatus(TopicStatus status, Pageable pageable);

    Page<Topic> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<Topic> findByStatusAndCreatedAtBetween(TopicStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // 아직 AI 요약이 없는 ACTIVE 토픽 일괄 처리용
    List<Topic> findByAiSummaryIsNullAndStatus(TopicStatus status);
}
