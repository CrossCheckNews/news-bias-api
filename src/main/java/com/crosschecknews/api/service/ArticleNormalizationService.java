package com.crosschecknews.api.service;

import com.crosschecknews.api.domain.Publisher;
import com.crosschecknews.api.dto.ArticleCandidate;
import com.crosschecknews.api.dto.NormalizedArticleCandidate;
import com.crosschecknews.api.exception.ResourceNotFoundException;
import com.crosschecknews.api.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleNormalizationService {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid", "msclkid", "ref", "source", "outputType"
    );

    private final PublisherRepository publisherRepository;

    public NormalizedArticleCandidate normalize(ArticleCandidate candidate) {
        Publisher publisher = publisherRepository.findByName(candidate.getPublisherName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publisher not found: " + candidate.getPublisherName()));

        String headline          = sanitizeText(candidate.getHeadline());
        String normalizedHeadline = normalizeHeadline(headline);
        String normalizedUrl     = normalizeUrl(candidate.getUrl());
        String dedupeKey         = generateDedupeKey(publisher.getName(), normalizedHeadline, candidate);

        return NormalizedArticleCandidate.builder()
                .headline(headline)
                .normalizedHeadline(normalizedHeadline)
                .url(candidate.getUrl())
                .normalizedUrl(normalizedUrl)
                .description(sanitizeHtml(candidate.getDescription()))
                .rssGuid(candidate.getRssGuid())
                .dedupeKey(dedupeKey)
                .publishedAt(candidate.getPublishedAt())
                .publisher(publisher)
                .category(candidate.getCategory())
                .build();
    }

    // ── URL 정규화 ────────────────────────────────────────────────────────────

    String normalizeUrl(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            URI uri = new URI(rawUrl.trim());
            String path = stripTrailingSlash(uri.getPath());
            String cleanQuery = buildCleanQuery(uri.getQuery());

            // http / https 모두 https로 통일
            URI normalized = new URI(
                    "https",
                    uri.getHost(),
                    path.isEmpty() ? "/" : path,
                    cleanQuery.isEmpty() ? null : cleanQuery,
                    null
            );
            return normalized.toString().toLowerCase();
        } catch (Exception e) {
            log.warn("URL 정규화 실패, 원본 소문자 사용 url={} cause={}", rawUrl, e.getMessage());
            return rawUrl.toLowerCase().trim();
        }
    }

    private String stripTrailingSlash(String path) {
        if (path == null || path.equals("/")) return "";
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String buildCleanQuery(String query) {
        if (query == null || query.isBlank()) return "";
        return Arrays.stream(query.split("&"))
                .filter(param -> !param.isBlank())
                .filter(param -> {
                    String key = param.split("=")[0].toLowerCase();
                    return !TRACKING_PARAMS.contains(key);
                })
                .collect(Collectors.joining("&"));
    }

    // ── 헤드라인 정규화 ───────────────────────────────────────────────────────

    String normalizeHeadline(String headline) {
        if (headline == null) return "";
        return headline
                .toLowerCase()
                .replaceAll("[\\p{Punct}]", " ")  // 구두점 → 공백
                .replaceAll("\\s{2,}", " ")        // 연속 공백 단일화
                .trim();
    }

    // ── dedupeKey 생성 ────────────────────────────────────────────────────────

    // SHA-256(publisherName | normalizedHeadline | publishedDate)
    // publishedAt이 null이면 "unknown" 사용 (guid/url fallback에 의존)
    String generateDedupeKey(String publisherName, String normalizedHeadline, ArticleCandidate candidate) {
        String dateStr = candidate.getPublishedAt() != null
                ? candidate.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : "unknown";
        String raw = publisherName.toLowerCase() + "|" + normalizedHeadline + "|" + dateStr;
        return sha256(raw);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.error("SHA-256 생성 실패 input={}", input, e);
            return input; // fallback: 원문 그대로 (충돌 가능하나 SHA-256은 항상 사용 가능)
        }
    }

    // ── 텍스트 정제 ───────────────────────────────────────────────────────────

    private String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return null;
        String result = html
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&apos;", "'")
                .replaceAll("&#39;", "'")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s{2,}", " ")
                .trim();
        // 한겨례처럼 이미지 태그만 있어 제거 후 텍스트가 없는 경우 null 반환
        return result.isBlank() ? null : result;
    }

    private String sanitizeText(String text) {
        if (text == null) return null;
        return text
                .replaceAll("&apos;", "'")
                .replaceAll("&#39;", "'")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
