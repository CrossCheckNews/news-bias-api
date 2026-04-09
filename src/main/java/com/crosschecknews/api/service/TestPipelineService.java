package com.crosschecknews.api.service;

import com.crosschecknews.api.client.FallbackAiClient;
import com.crosschecknews.api.domain.*;
import com.crosschecknews.api.dto.TestPipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DB를 거치지 않고 XML 파일에서 기사를 읽어 파이프라인 3단계를 검증한다.
 *
 * <p>Step 1: XML 파싱 (fetch-save 대체)
 * <p>Step 2: 전체 기사를 하나의 토픽으로 묶음 (clustering 대체)
 * <p>Step 3: Gemini 호출로 AI 요약 생성 (summarize)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPipelineService {

    private static final String TEST_DATA_PATH = "test-data/test-articles.xml";

    private final FallbackAiClient geminiClient;
    private final PromptBuilder promptBuilder;

    public TestPipelineResult run() {
        // Step 1: XML 파싱
        List<TopicArticle> topicArticles = loadArticlesFromXml();
        log.info("[Test Step 1] XML 파싱 완료 articles={}", topicArticles.size());

        // Step 2: 전체를 하나의 토픽으로 그룹화 (첫 번째 기사 헤드라인을 토픽 제목으로 사용)
        String topicTitle = topicArticles.get(0).getArticle().getHeadline();
        log.info("[Test Step 2] 토픽 그룹화 완료 title='{}'", topicTitle);

        // Step 3: AI 요약
        String prompt = promptBuilder.buildSummaryPrompt(topicArticles);
        String aiSummary = geminiClient.generate(prompt);
        String aiModel = geminiClient.getModel();
        log.info("[Test Step 3] AI 요약 완료 model={} chars={}", aiModel, aiSummary.length());

        List<TestPipelineResult.ArticleInfo> articleInfos = topicArticles.stream()
                .map(ta -> TestPipelineResult.ArticleInfo.builder()
                        .publisher(ta.getArticle().getPublisher().getName())
                        .country(ta.getArticle().getPublisher().getCountry().name())
                        .politicalLeaning(ta.getArticle().getPublisher().getPoliticalLeaning().name())
                        .headline(ta.getArticle().getHeadline())
                        .description(ta.getArticle().getDescription())
                        .build())
                .toList();

        return TestPipelineResult.builder()
                .articles(articleInfos)
                .topicTitle(topicTitle)
                .aiSummary(aiSummary)
                .aiModel(aiModel)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── XML 파싱 ──────────────────────────────────────────────────────────────

    private List<TopicArticle> loadArticlesFromXml() {
        try (InputStream is = new ClassPathResource(TEST_DATA_PATH).getInputStream()) {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(is);
            doc.getDocumentElement().normalize();

            NodeList nodes = doc.getElementsByTagName("article");
            List<TopicArticle> result = new ArrayList<>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                result.add(toTopicArticle(el));
            }

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("테스트 XML 파일 로드 실패: " + TEST_DATA_PATH, e);
        }
    }

    private TopicArticle toTopicArticle(Element el) {
        Element pub = (Element) el.getElementsByTagName("publisher").item(0);

        Publisher publisher = Publisher.builder()
                .name(pub.getAttribute("name"))
                .country(Country.valueOf(pub.getAttribute("country")))
                .politicalLeaning(PoliticalLeaning.valueOf(pub.getAttribute("politicalLeaning")))
                .build();

        Article article = Article.builder()
                .headline(text(el, "headline"))
                .normalizedHeadline(text(el, "headline").toLowerCase().trim())
                .url(text(el, "url"))
                .normalizedUrl(text(el, "url"))
                .description(text(el, "description").trim())
                .category(Category.WORLD)
                .rssGuid(text(el, "url"))
                .dedupeKey("test-" + text(el, "url"))
                .publisher(publisher)
                .build();

        return TopicArticle.builder()
                .article(article)
                .build();
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().strip();
    }
}
