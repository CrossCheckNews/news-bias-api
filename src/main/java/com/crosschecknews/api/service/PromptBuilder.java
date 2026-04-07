package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.TopicArticle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    /**
     * 토픽에 연결된 기사 목록으로 AI 요약 프롬프트를 구성한다.
     *
     * <p>기사 본문은 사용하지 않는다. headline + description + publisherName만 사용.
     */
    public String buildSummaryPrompt(List<TopicArticle> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 동일한 이슈를 다루는 여러 언론사의 뉴스 헤드라인과 간략한 설명입니다.\n\n");
        sb.append("[기사 목록]\n");

        for (TopicArticle ta : articles) {
            String publisher = ta.getArticle().getPublisher().getName();
            String headline  = ta.getArticle().getHeadline();
            String desc      = ta.getArticle().getDescription();

            sb.append("- [").append(publisher).append("] ").append(headline);
            if (desc != null && !desc.isBlank()) {
                // description은 최대 120자로 잘라 프롬프트 길이를 제한한다
                String trimmed = desc.length() > 120 ? desc.substring(0, 120) + "…" : desc;
                sb.append(" / ").append(trimmed);
            }
            sb.append("\n");
        }

        sb.append("\n");
        sb.append("위 기사들이 다루는 이슈의 핵심을 1~2문장으로 팩트 중심으로 요약해주세요.\n");
        sb.append("언론사 성향 비교나 분석은 하지 말고, 공통된 사실만 간결하게 서술해주세요.\n");
        sb.append("한국어로 답변하고, 요약문만 출력해주세요. 다른 설명은 불필요합니다.");

        return sb.toString();
    }
}
