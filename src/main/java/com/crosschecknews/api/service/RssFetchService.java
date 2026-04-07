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
            return feed.getEntries().stream()
                    .map(this::toRssEntry)
                    .filter(e -> e.link() != null && !e.link().isBlank())
                    .toList();
        } catch (Exception e) {
            throw new RssFetchException(rssUrl, e);
        }
    }

    private RssEntry toRssEntry(SyndEntry entry) {
        LocalDateTime publishedAt = null;
        if (entry.getPublishedDate() != null) {
            publishedAt = entry.getPublishedDate()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        String description = null;
        if (entry.getDescription() != null) {
            description = entry.getDescription().getValue();
        }

        // guid: RSS <guid> 또는 Atom <id> — 없으면 link로 fallback
        String guid = entry.getUri() != null ? entry.getUri() : entry.getLink();

        return new RssEntry(entry.getTitle(), entry.getLink(), description, guid, publishedAt);
    }

    public record RssEntry(String title, String link, String description, String guid, LocalDateTime publishedAt) {}
}
