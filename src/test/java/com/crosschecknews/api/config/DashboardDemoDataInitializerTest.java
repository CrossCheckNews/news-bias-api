package com.crosschecknews.api.config;

import com.crosschecknews.api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
class DashboardDemoDataInitializerTest {

    @Autowired DashboardDemoDataInitializer initializer;

    @Autowired ArticleRepository articleRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired PipelineRunRepository pipelineRunRepository;
    @Autowired PipelineStepHistoryRepository pipelineStepHistoryRepository;

    // ApplicationRunner는 스프링 컨텍스트 기동 시 자동 실행되므로
    // 테스트 시작 시점에 이미 demo 데이터가 삽입된 상태

    @Test
    void demo_profile에서_article이_최소_1개_이상_생성된다() {
        assertThat(articleRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void demo_profile에서_topic이_최소_1개_이상_생성된다() {
        assertThat(topicRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void demo_profile에서_pipelineRun이_최소_1개_이상_생성된다() {
        assertThat(pipelineRunRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void demo_profile에서_pipelineStepHistory가_최소_1개_이상_생성된다() {
        assertThat(pipelineStepHistoryRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 중복_실행해도_데이터가_추가로_생성되지_않는다() throws Exception {
        long articlesBefore = articleRepository.count();
        long topicsBefore   = topicRepository.count();

        // 데이터가 이미 있으므로 initializer는 early-return
        initializer.run((ApplicationArguments) null);

        assertThat(articleRepository.count()).isEqualTo(articlesBefore);
        assertThat(topicRepository.count()).isEqualTo(topicsBefore);
    }
}
