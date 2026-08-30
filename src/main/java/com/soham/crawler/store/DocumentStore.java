package com.soham.crawler.store;

import com.soham.crawler.search.IndexedDocument;
import java.util.List;

public interface DocumentStore {
    void save(IndexedDocument document);

    List<IndexedDocument> findAll();

    long count();
}
