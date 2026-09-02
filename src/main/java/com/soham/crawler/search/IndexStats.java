package com.soham.crawler.search;

public record IndexStats(int documents, int uniqueTerms, long postings, long totalTokens) {}
