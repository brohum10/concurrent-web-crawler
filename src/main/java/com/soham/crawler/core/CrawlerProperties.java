package com.soham.crawler.core;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        int workers,
        int maxPages,
        Duration perHostDelay,
        Duration requestTimeout,
        int maxRetries,
        String userAgent) {

    public CrawlerProperties {
        if (workers < 1 || workers > 64) {
            throw new IllegalArgumentException("workers must be between 1 and 64");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        if (maxRetries < 0 || maxRetries > 5) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 5");
        }
    }
}
