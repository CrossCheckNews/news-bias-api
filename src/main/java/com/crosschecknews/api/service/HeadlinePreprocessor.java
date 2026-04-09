package com.crosschecknews.api.service;

import org.springframework.stereotype.Component;

/**
 * Embedding API 입력용 headline 전처리기.
 * 과한 정규화 없이 기본 trim만 수행한다.
 * 원본 headline은 그대로 보존되어야 하므로 이 클래스에서 소문자화/기호 제거는 하지 않는다.
 */
@Component
public class HeadlinePreprocessor {

    public String prepare(String headline) {
        if (headline == null) return "";
        return headline.strip();
    }
}
