package com.crosschecknews.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final RestClient restClient;

    public DemoDataInitializer(@Value("${server.port:8080}") int port) {
        this.restClient = RestClient.create("http://localhost:" + port);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("데모 데이터 초기화 시작");
            restClient.post()
                    .uri("/api/v1/test/pipeline/demo-load")
                    .retrieve()
                    .toBodilessEntity();
            log.info("데모 데이터 초기화 완료");
        } catch (Exception e) {
            log.warn("데모 데이터 초기화 실패: {}", e.getMessage());
        }
    }
}
