package com.soham.crawler.store;

import com.soham.crawler.search.IndexedDocument;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDocumentStore implements DocumentStore {
    private final JdbcTemplate jdbc;

    public JdbcDocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id VARCHAR(64) PRIMARY KEY,
                    url TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    crawled_at TIMESTAMPTZ NOT NULL
                )
                """);
    }

    @Override
    public void save(IndexedDocument document) {
        jdbc.update("""
                        INSERT INTO documents(id, url, title, content, crawled_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                          url = EXCLUDED.url,
                          title = EXCLUDED.title,
                          content = EXCLUDED.content,
                          crawled_at = EXCLUDED.crawled_at
                        """,
                document.id(), document.url(), document.title(), document.content(), Timestamp.from(document.crawledAt()));
    }

    @Override
    public List<IndexedDocument> findAll() {
        return jdbc.query("SELECT id, url, title, content, crawled_at FROM documents ORDER BY crawled_at",
                (result, row) -> new IndexedDocument(
                        result.getString("id"),
                        result.getString("url"),
                        result.getString("title"),
                        result.getString("content"),
                        result.getTimestamp("crawled_at").toInstant()));
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        return count == null ? 0 : count;
    }
}
