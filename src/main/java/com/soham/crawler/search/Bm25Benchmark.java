package com.soham.crawler.search;

import java.time.Instant;
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
        int queryHits = 0;
        double reciprocalRankSum = 0.0;
        double precisionSum = 0.0;
        for (int query = 0; query < queries; query++) {
            int topic = query % 250;
            long started = System.nanoTime();
            List<SearchHit> hits = index.search("topic" + topic + " reliability", 10);
            latencies[query] = System.nanoTime() - started;
            int relevant = 0;
            for (int position = 0; position < hits.size(); position++) {
                // Exact equality matters: "Topic 1" must not match "Topic 10".
                if (hits.get(position).title().equals("Topic " + topic + " reference")) {
                    relevant++;
                    if (relevant == 1) {
                        reciprocalRankSum += 1.0 / (position + 1);
                    }
                }
            }
            precisionSum += hits.isEmpty() ? 0.0 : (double) relevant / hits.size();
            if (relevant > 0) queryHits++;
        }
        Arrays.sort(latencies);
        System.out.printf("documents=%d queries=%d hit_rate_at_10=%.3f precision_at_10=%.3f mrr_at_10=%.3f p50_ms=%.3f p95_ms=%.3f%n",
                documents,
                queries,
                (double) queryHits / queries,
                precisionSum / queries,
                reciprocalRankSum / queries,
                percentileMillis(latencies, 0.50),
                percentileMillis(latencies, 0.95));
    }

    private static double percentileMillis(long[] values, double percentile) {
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index] / 1_000_000.0;
    }
}
