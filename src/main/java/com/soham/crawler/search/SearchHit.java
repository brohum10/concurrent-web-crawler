package com.soham.crawler.search;

import java.time.Instant;
import java.util.List;

public record SearchHit(
        String id,
        String url,
        String title,
        String snippet,
        double score,
        List<String> matchedTerms,
        Instant crawledAt) {}
