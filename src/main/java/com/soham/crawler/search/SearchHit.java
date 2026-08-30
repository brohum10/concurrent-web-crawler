package com.soham.crawler.search;

public record SearchHit(String id, String url, String title, String snippet, double score) {}
