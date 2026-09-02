package com.soham.crawler.core;

import java.time.Instant;

public record CrawlJobSnapshot(
        String id,
        CrawlJobStatus status,
        String seed,
        CrawlOptions options,
        CrawlProgress progress,
        CrawlReport report,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {}

