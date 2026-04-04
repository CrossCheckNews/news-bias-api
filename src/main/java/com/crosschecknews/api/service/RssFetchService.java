package com.crosschecknews.api.service;

// Design Ref: §6.3 — Rome SyndFeedInput for RSS/Atom parsing
import com.crosschecknews.api.exception.RssFetchException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

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
        return new RssEntry(entry.getTitle(), entry.getLink(), publishedAt);
    }

    public record RssEntry(String title, String link, LocalDateTime publishedAt) {}
}
