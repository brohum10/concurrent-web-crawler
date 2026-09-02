package com.soham.crawler.search;

import com.soham.crawler.core.FetchedPage;
import com.soham.crawler.store.DocumentStore;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final int MAX_CONTENT_LENGTH = 1_000_000;
    private final DocumentStore store;
    private final Bm25Index index;

    public SearchService(DocumentStore store, Bm25Index index) {
        this.store = store;
        this.index = index;
    }

    @PostConstruct
    void restoreIndex() {
        store.findAll().forEach(index::add);
    }

    public IndexedDocument index(FetchedPage page) {
        String content = page.content().length() <= MAX_CONTENT_LENGTH
                ? page.content()
                : page.content().substring(0, MAX_CONTENT_LENGTH);
        IndexedDocument document = new IndexedDocument(
                sha256(page.url().toString()), page.url().toString(), page.title(), content, Instant.now());
        store.save(document);
        index.add(document);
        return document;
    }

    public List<SearchHit> search(String query, int limit) {
        return index.search(query, Math.min(Math.max(limit, 1), 50));
    }

    public int indexedCount() {
        return index.size();
    }

    public IndexStats stats() {
        return index.stats();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
