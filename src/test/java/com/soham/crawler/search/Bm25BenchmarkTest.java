package com.soham.crawler.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Bm25BenchmarkTest {
    @Test
    void reportsQueryHitRateNotDocumentRecall() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            Bm25Benchmark.main(new String[] {"500", "20"});
        } finally {
            System.setOut(original);
        }
        String report = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("hit_rate_at_10="));
        assertTrue(report.contains("precision_at_10="));
        assertTrue(report.contains("mrr_at_10="));
        assertFalse(report.contains("recall_at_10="));
    }
}
