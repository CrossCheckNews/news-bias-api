package com.crosschecknews.api.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedSource {

    FOX_WORLD(
            "Fox News", Country.US, PoliticalLeaning.CONSERVATIVE,
            Category.WORLD, "https://moxie.foxnews.com/google-publisher/world.xml"
    ),
    NYT_WORLD(
            "New York Times", Country.US, PoliticalLeaning.PROGRESSIVE,
            Category.WORLD, "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"
    ),
    CHOSUN_WORLD(
            "Chosun Ilbo", Country.KR, PoliticalLeaning.CONSERVATIVE,
            Category.WORLD, "https://www.chosun.com/arc/outboundfeeds/rss/category/international/?outputType=xml"
    ),
    HANI_WORLD(
            "Hankyoreh", Country.KR, PoliticalLeaning.PROGRESSIVE,
            Category.WORLD, "https://www.hani.co.kr/rss/international"
    );

    private final String publisherName;
    private final Country country;
    private final PoliticalLeaning politicalLeaning;
    private final Category category;
    private final String rssUrl;
}
