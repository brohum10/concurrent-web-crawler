package com.soham.crawler.core;

import com.soham.crawler.search.SearchService;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.stereotype.Service;

@Service
public class CrawlerService {
    private final CrawlerProperties properties;
    private final SearchService searchService;

    public CrawlerService(CrawlerProperties properties, SearchService searchService) {
        this.properties = properties;
        this.searchService = searchService;
    }

    public CrawlReport crawl(String seed, Integer requestedMaxPages) {
        int maxPages = requestedMaxPages == null ? properties.maxPages() : requestedMaxPages;
        if (maxPages < 1 || maxPages > properties.maxPages()) {
            throw new IllegalArgumentException("maxPages must be between 1 and " + properties.maxPages());
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        ConcurrentCrawler crawler = new ConcurrentCrawler(
                new HttpPageFetcher(client, properties.requestTimeout(), properties.userAgent(), properties.maxRetries()),
                new RobotsTxtPolicy(client, properties.requestTimeout(), properties.userAgent()),
                new HostSafetyPolicy(),
                new HostRateLimiter(properties.perHostDelay()),
                properties.workers());
        return crawler.crawl(URI.create(seed), maxPages, searchService::index);
    }
}
