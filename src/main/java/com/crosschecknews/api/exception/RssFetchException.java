package com.crosschecknews.api.exception;

public class RssFetchException extends RuntimeException {
    public RssFetchException(String url, Throwable cause) {
        super("RSS fetch failed for URL: " + url, cause);
    }
}
