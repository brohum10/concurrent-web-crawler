package com.soham.crawler.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Bm25Benchmark {
    private Bm25Benchmark() {}

    public static void main(String[] args) {
        int documents = args.length > 0 ? Integer.parseInt(args[0]) : 25_000;
        int queries = args.length > 1 ? Integer.parseInt(args[1]) : 1_000;
        Bm25Index index = new Bm25Index();
        for (int id = 0; id < documents; id++) {
            int topic = id % 250;
            index.add(new IndexedDocument(
                    Integer.toString(id),
                    "https://benchmark.local/docs/" + id,
                    "Topic " + topic + " reference",
                    "topic" + topic + " distributed systems search indexing reliability document" + id,
                    Instant.EPOCH));
        }

        long[] latencies = new long[queries];
        int correct = 0;
        for (int query = 0; query < queries; query++) {
            int topic = query % 250;
            long started = System.nanoTime();
            List<SearchHit> hits = index.search("topic" + topic + " reliability", 10);
            latencies[query] = System.nanoTime() - started;
            if (hits.stream().anyMatch(hit -> hit.title().contains("Topic " + topic))) {
                correct++;
            }
        }
        Arrays.sort(latencies);
        System.out.printf("documents=%d queries=%d recall_at_10=%.3f p50_ms=%.3f p95_ms=%.3f%n",
                documents,
                queries,
                (double) correct / queries,
                percentileMillis(latencies, 0.50),
                percentileMillis(latencies, 0.95));
    }

    private static double percentileMillis(long[] values, double percentile) {
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index] / 1_000_000.0;
    }
}
