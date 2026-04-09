package com.crosschecknews.api.client;

public interface AiClient {
    String generate(String prompt);
    String getModel();
}
