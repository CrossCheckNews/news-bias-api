package com.crosschecknews.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RssCollectRequest {

    // null 또는 빈 리스트면 전체 FeedSource 수집
    private List<String> feedSourceCodes;

    public boolean isCollectAll() {
        return feedSourceCodes == null || feedSourceCodes.isEmpty();
    }
}
