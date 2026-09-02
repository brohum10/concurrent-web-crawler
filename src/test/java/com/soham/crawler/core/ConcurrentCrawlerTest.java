package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void honorsDepthAndScopeLimits() {
        URI seed = URI.create("https://example.com/start");
        URI child = URI.create("https://example.com/child");
        URI grandchild = URI.create("https://example.com/grandchild");
        URI external = URI.create("https://other.example/page");
        Map<URI, FetchedPage> pages = Map.of(
                seed, page(seed, List.of(child, external)),
                child, page(child, List.of(grandchild)),
                grandchild, page(grandchild, List.of()),
                external, page(external, List.of()));
        ConcurrentCrawler crawler = crawler(pages::get, 2);

        CrawlReport report = crawler.crawl(
                seed,
                new CrawlOptions(10, 1, CrawlScope.SAME_HOST),
                () -> false,
                ignored -> {},
                ignored -> {});

        assertEquals(2, report.indexed());
        assertEquals(1, report.maxDepthReached());
        assertTrue(report.skipped() >= 1);
    }

    @Test
    void reportsProgressAndSupportsCooperativeCancellation() {
        URI seed = URI.create("https://example.com/start");
        URI child = URI.create("https://example.com/child");
        Map<URI, FetchedPage> pages = Map.of(
                seed, page(seed, List.of(child)),
                child, page(child, List.of()));
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean sawProgress = new AtomicBoolean();
        ConcurrentCrawler crawler = crawler(pages::get, 1);

        CrawlReport report = crawler.crawl(
                seed,
                new CrawlOptions(10, 3, CrawlScope.SAME_HOST),
                cancelled::get,
                ignored -> sawProgress.set(true),
                ignored -> cancelled.set(true));

        assertEquals(1, report.indexed());
        assertTrue(report.cancelled());
        assertTrue(sawProgress.get());
    }

    private static ConcurrentCrawler crawler(PageFetcher fetcher, int workers) {
        HostSafetyPolicy allowFixtureHosts = new HostSafetyPolicy() {
            @Override
            public boolean isAllowed(URI ignored) {
                return true;
            }
        };
        return new ConcurrentCrawler(
                fetcher,
                ignored -> true,
                allowFixtureHosts,
                new HostRateLimiter(Duration.ZERO),
                workers);
    }

    private static FetchedPage page(URI uri, List<URI> links) {
        return new FetchedPage(uri, uri.getPath(), "reliable distributed systems", links);
    }
}
