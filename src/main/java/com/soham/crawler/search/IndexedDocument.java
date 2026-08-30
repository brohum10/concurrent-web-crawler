package com.soham.crawler.search;

import java.time.Instant;

public record IndexedDocument(String id, String url, String title, String content, Instant crawledAt) {}
