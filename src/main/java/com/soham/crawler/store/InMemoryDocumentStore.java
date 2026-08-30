package com.soham.crawler.store;

import com.soham.crawler.search.IndexedDocument;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDocumentStore implements DocumentStore {
    private final ConcurrentHashMap<String, IndexedDocument> documents = new ConcurrentHashMap<>();

    @Override
    public void save(IndexedDocument document) {
        documents.put(document.id(), document);
    }

    @Override
    public List<IndexedDocument> findAll() {
        return List.copyOf(documents.values());
    }

    @Override
    public long count() {
        return documents.size();
    }
}
