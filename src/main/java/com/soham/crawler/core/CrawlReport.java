package com.soham.crawler.core;

import java.time.Duration;

public record CrawlReport(
        int scheduled,
        int indexed,
        int failed,
        int skipped,
        long bytesProcessed,
        int maxDepthReached,
        boolean cancelled,
        Duration elapsed) {}
