package com.soham.crawler.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.soham.crawler.core.FetchedPage;
import com.soham.crawler.store.InMemoryDocumentStore;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchServiceTest {
    @Test
    void indexesFetchedPagesAndReturnsSearchHits() {
        SearchService service = new SearchService(new InMemoryDocumentStore(), new Bm25Index());
        service.restoreIndex();
        service.index(new FetchedPage(
                URI.create("https://example.com/search"),
                "Search architecture",
                "inverted index bm25 ranking priority queue",
                List.of()));

        assertEquals(1, service.indexedCount());
        assertEquals("Search architecture", service.search("bm25 ranking", 10).get(0).title());
    }
}
