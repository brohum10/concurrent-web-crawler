package com.soham.crawler.core;

public record CrawlOptions(int maxPages, int maxDepth, CrawlScope scope) {
    public CrawlOptions {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        if (maxDepth < 0 || maxDepth > 50) {
            throw new IllegalArgumentException("maxDepth must be between 0 and 50");
        }
        scope = scope == null ? CrawlScope.SAME_HOST : scope;
    }
}

