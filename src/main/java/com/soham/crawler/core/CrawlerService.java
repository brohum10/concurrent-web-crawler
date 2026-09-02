package com.soham.crawler.core;

import com.soham.crawler.search.SearchService;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class CrawlerService {
    private static final int DEFAULT_MAX_DEPTH = 4;

    private final CrawlerProperties properties;
    private final SearchService searchService;
    private final ConcurrentCrawler crawler;

    public CrawlerService(CrawlerProperties properties, SearchService searchService) {
        this.properties = properties;
        this.searchService = searchService;
        HostSafetyPolicy safetyPolicy = new HostSafetyPolicy();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.crawler = new ConcurrentCrawler(
                new HttpPageFetcher(
                        client,
                        properties.requestTimeout(),
                        properties.userAgent(),
                        properties.maxRetries(),
                        properties.maxResponseBytes(),
                        safetyPolicy),
                new RobotsTxtPolicy(client, properties.requestTimeout(), properties.userAgent()),
                safetyPolicy,
                new HostRateLimiter(properties.perHostDelay()),
                properties.workers());
    }

    public CrawlOptions options(Integer requestedMaxPages, Integer requestedMaxDepth, CrawlScope requestedScope) {
        int maxPages = requestedMaxPages == null ? properties.maxPages() : requestedMaxPages;
        if (maxPages < 1 || maxPages > properties.maxPages()) {
            throw new IllegalArgumentException("maxPages must be between 1 and " + properties.maxPages());
        }
        int maxDepth = requestedMaxDepth == null ? DEFAULT_MAX_DEPTH : requestedMaxDepth;
        return new CrawlOptions(maxPages, maxDepth, requestedScope == null ? CrawlScope.SAME_HOST : requestedScope);
    }

    public CrawlReport crawl(String seed, Integer requestedMaxPages) {
        return crawl(
                seed,
                options(requestedMaxPages, null, null),
                () -> false,
                ignored -> {});
    }

    public CrawlReport crawl(
            String seed,
            CrawlOptions options,
            BooleanSupplier cancelled,
            Consumer<CrawlProgress> progressConsumer) {
        return crawler.crawl(URI.create(seed), options, cancelled, progressConsumer, searchService::index);
    }
}
