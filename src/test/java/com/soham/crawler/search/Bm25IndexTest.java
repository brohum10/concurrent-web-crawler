package com.soham.crawler.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Bm25IndexTest {
    @Test
    void ranksDocumentsByQueryTermRelevance() {
        Bm25Index index = new Bm25Index();
        index.add(document("1", "Java concurrency", "threads queues locks executor services"));
        index.add(document("2", "Python data", "pandas dataframe validation pipeline"));
        index.add(document("3", "Web design", "responsive layout color typography"));

        List<SearchHit> results = index.search("java threads concurrency", 2);

        assertEquals("1", results.get(0).id());
        assertTrue(results.get(0).score() > 0.0);
    }

    @Test
    void replacesExistingDocumentsWithoutDuplicatingThem() {
        Bm25Index index = new Bm25Index();
        index.add(document("1", "Old title", "python"));
        index.add(document("1", "New title", "java concurrency"));

        assertEquals(1, index.size());
        assertEquals("New title", index.search("java", 10).get(0).title());
    }

    @Test
    void boostsTitleMatchesAndReturnsExplainableMetadata() {
        Bm25Index index = new Bm25Index();
        index.add(document("title", "Distributed tracing", "observability guide"));
        index.add(document("body", "Operations", "distributed systems and tracing fundamentals"));

        SearchHit first = index.search("distributed tracing", 2).get(0);

        assertEquals("title", first.id());
        assertEquals(List.of("distributed", "tracing"), first.matchedTerms());
        assertEquals(Instant.EPOCH, first.crawledAt());
    }

    @Test
    void buildsQueryAwareSnippetsAndReportsIndexStats() {
        Bm25Index index = new Bm25Index();
        String prefix = "background ".repeat(35);
        index.add(document("1", "Architecture", prefix + "backpressure protects the worker queue from overload"));

        SearchHit hit = index.search("backpressure", 1).get(0);
        IndexStats stats = index.stats();

        assertTrue(hit.snippet().contains("backpressure"));
        assertTrue(hit.snippet().startsWith("..."));
        assertEquals(1, stats.documents());
        assertTrue(stats.uniqueTerms() > 3);
        assertTrue(stats.totalTokens() > 0);
    }

    private static IndexedDocument document(String id, String title, String content) {
        return new IndexedDocument(id, "https://example.com/" + id, title, content, Instant.EPOCH);
    }
}
