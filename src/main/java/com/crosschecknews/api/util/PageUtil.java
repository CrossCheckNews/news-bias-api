package com.crosschecknews.api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageUtil {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;

    private PageUtil() {}

    public static PageRequest of(int page, int size, Sort.Order... orders) {
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
