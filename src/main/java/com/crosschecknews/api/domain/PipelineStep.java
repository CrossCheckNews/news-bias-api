package com.crosschecknews.api.domain;

public enum PipelineStep {
    RSS_COLLECT,
    ARTICLE_SAVE,
    TOPIC_CLUSTERING,
    AI_SUMMARY,
    COMPLETED
}
