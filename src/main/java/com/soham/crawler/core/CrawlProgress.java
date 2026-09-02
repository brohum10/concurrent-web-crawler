package com.soham.crawler.core;

public record CrawlProgress(
        int scheduled,
        int indexed,
        int failed,
        int skipped,
        long bytesProcessed,
        int maxDepthReached) {

    public static CrawlProgress empty() {
        return new CrawlProgress(0, 0, 0, 0, 0, 0);
    }
}

