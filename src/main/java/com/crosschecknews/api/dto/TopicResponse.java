package com.crosschecknews.api.dto;

import com.crosschecknews.api.domain.Topic;
import com.crosschecknews.api.domain.TopicArticle;
import com.crosschecknews.api.domain.TopicStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "토픽 응답 (목록 및 단건 공통)")
public class TopicResponse {

    @Schema(description = "토픽 ID", example = "1")
    private Long id;

    @Schema(description = "AI가 생성한 토픽 제목", example = "반도체 수출 규제 논란")
    private String aiSummaryTitle;

    @Schema(
        description = "언론사별 대표 헤드라인. key=언론사명, value=헤드라인",
        example = "{\"조선일보\": \"정부, 반도체 수출 규제 완화 검토\", \"한겨레\": \"수출 규제 완화, 재벌 특혜 우려\"}"
    )
    private Map<String, String> summary;

    @Schema(description = "AI 브리핑 요약문", example = "여야가 반도체 수출 규제 완화를 두고 엇갈린 입장을 보이고 있다.")
    private String aiSummary;

    @Schema(description = "토픽 상태", allowableValues = {"PENDING", "ACTIVE", "ARCHIVED"}, example = "ACTIVE")
    private TopicStatus status;

    @Schema(description = "토픽 시작일 (yyyy-MM-dd)", example = "2026-04-09")
    private LocalDate startDate;

    @Schema(description = "연결된 기사 수", example = "5")
    private long articleCount;

    @Schema(
        description = "정치 성향별 기사 수. key=성향(CONSERVATIVE|PROGRESSIVE), value=기사 수",
        example = "{\"CONSERVATIVE\": 3, \"PROGRESSIVE\": 2}"
    )
    private Map<String, Integer> leaningDistribution;

    @Schema(
        description = "국가별 기사 수. key=국가코드(KR|US|JP 등), value=기사 수",
        example = "{\"KR\": 4, \"US\": 1}"
    )
    private Map<String, Integer> countryDistribution;

    /**
     * 연결된 기사 목록을 포함해 응답 객체를 생성한다.
     * summary: 언론사명 → 해당 기사 headline
     */
    public static TopicResponse from(Topic topic, List<TopicArticle> links) {
        Map<String, String> summary = links.stream()
                .collect(Collectors.toMap(
                        ta -> ta.getArticle().getPublisher().getName(),
                        ta -> ta.getArticle().getHeadline(),
                        (first, ignored) -> first  // 동일 언론사 기사 중복 시 첫 번째 유지
                ));

        Map<String, Integer> leaningDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getPoliticalLeaning().name(),
                        Collectors.summingInt(x -> 1)
                ));

        Map<String, Integer> countryDistribution = links.stream()
                .collect(Collectors.groupingBy(
                        ta -> ta.getArticle().getPublisher().getCountry().name(),
                        Collectors.summingInt(x -> 1)
                ));

        return TopicResponse.builder()
                .id(topic.getId())
                .aiSummaryTitle(topic.getTitle())
                .summary(summary)
                .aiSummary(topic.getAiSummary())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .articleCount(links.size())
                .leaningDistribution(leaningDistribution)
                .countryDistribution(countryDistribution)
                .build();
    }

    /** 기사가 아직 없는 신규 Topic 생성 직후용 */
    public static TopicResponse empty(Topic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .aiSummaryTitle(topic.getTitle())
                .summary(Collections.emptyMap())
                .aiSummary(topic.getAiSummary())
                .status(topic.getStatus())
                .startDate(topic.getStartDate())
                .articleCount(0)
                .leaningDistribution(Collections.emptyMap())
                .countryDistribution(Collections.emptyMap())
                .build();
    }
}
