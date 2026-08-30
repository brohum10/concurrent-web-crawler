package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ConcurrentCrawlerTest {
    @Test
    void crawlsEachCanonicalUrlOnceAcrossCycles() {
        URI a = URI.create("https://example.com/a");
        URI b = URI.create("https://example.com/b");
        URI c = URI.create("https://example.com/c");
        URI d = URI.create("https://example.com/d");
        Map<URI, FetchedPage> pages = Map.of(
                a, page(a, List.of(b, c, b)),
                b, page(b, List.of(d)),
                c, page(c, List.of(d, a)),
                d, page(d, List.of()));
        Map<URI, Integer> fetchCounts = new ConcurrentHashMap<>();
        PageFetcher fetcher = uri -> {
            fetchCounts.merge(uri, 1, Integer::sum);
            return pages.get(uri);
        };
        HostSafetyPolicy allowFixtureHosts = new HostSafetyPolicy() {
            @Override
            public boolean isAllowed(URI ignored) {
                return true;
            }
        };
        ConcurrentCrawler crawler = new ConcurrentCrawler(
                fetcher,
                ignored -> true,
                allowFixtureHosts,
                new HostRateLimiter(Duration.ZERO),
                4);

        CrawlReport report = crawler.crawl(a, 10, ignored -> {});

        assertEquals(4, report.indexed());
        assertEquals(4, report.scheduled());
        assertEquals(0, report.failed());
        assertEquals(Map.of(a, 1, b, 1, c, 1, d, 1), fetchCounts);
    }

    private static FetchedPage page(URI uri, List<URI> links) {
        return new FetchedPage(uri, uri.getPath(), "reliable distributed systems", links);
    }
}
