package com.crosschecknews.api.service;

import com.crosschecknews.api.exception.RssFetchException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class RssFetchService {

    public List<RssEntry> fetch(String rssUrl) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(rssUrl)));
            // 아이템에 날짜가 없는 피드(한겨레 등)를 위 해 채널 레벨 날짜를 fallback으로 전달
            LocalDateTime feedLevelDate = toLocalDateTime(feed.getPublishedDate());
            return feed.getEntries().stream()
                    .map(entry -> toRssEntry(entry, feedLevelDate))
                    .filter(e -> e.link() != null && !e.link().isBlank())
                    .toList();
        } catch (Exception e) {
            throw new RssFetchException(rssUrl, e);
        }
    }

    private RssEntry toRssEntry(SyndEntry entry, LocalDateTime feedLevelDate) {
        // 1순위: 아이템 <pubDate> / Atom <published>
        // 2순위: 아이템 <updated>
        // 3순위: 채널 <lastBuildDate> (한겨레처럼 아이템 날짜가 없는 피드)
        var rawDate = entry.getPublishedDate() != null
                ? entry.getPublishedDate()
                : entry.getUpdatedDate();
        LocalDateTime publishedAt = rawDate != null
                ? toLocalDateTime(rawDate)
                : feedLevelDate;

        // 1순위: <description> (비어 있으면 무시)
        // 2순위: <content:encoded> — 조선일보처럼 description이 비어있고 content:encoded에 본문이 있는 경우
        String description = null;
        if (entry.getDescription() != null && !entry.getDescription().getValue().isBlank()) {
            description = entry.getDescription().getValue();
        } else if (!entry.getContents().isEmpty()) {
            description = entry.getContents().get(0).getValue();
        }

        // guid: RSS <guid> 또는 Atom <id> — 없으면 link로 fallback
        String guid = entry.getUri() != null ? entry.getUri() : entry.getLink();

        return new RssEntry(entry.getTitle(), entry.getLink(), description, guid, publishedAt);
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public record RssEntry(String title, String link, String description, String guid, LocalDateTime publishedAt) {}
}
