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

    private static IndexedDocument document(String id, String title, String content) {
        return new IndexedDocument(id, "https://example.com/" + id, title, content, Instant.EPOCH);
    }
}
