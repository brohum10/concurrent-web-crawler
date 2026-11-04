package com.soham.crawler.search;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Evaluates retrieval on Cranfield's human-judged aerospace queries. */
public final class CranfieldEvaluation {
    private CranfieldEvaluation() {}

    private static final class Record {
        private final StringBuilder title = new StringBuilder();
        private final StringBuilder body = new StringBuilder();

        String title() { return title.toString().trim(); }
        String body() { return body.toString().trim(); }
    }

    private record Scores(int queries, double hit, double mrr, double recall, double ndcg) {
        void print(String variant) {
            System.out.printf("variant=%s queries=%d hit_at_10=%.4f mrr_at_10=%.4f "
                    + "recall_at_10=%.4f ndcg_at_10=%.4f%n",
                    variant, queries, hit / queries, mrr / queries, recall / queries, ndcg / queries);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected directory containing cran.all.1400, cran.qry, and cranqrel");
        }
        Path directory = Path.of(args[0]);
        Map<Integer, Record> documents = readRecords(directory.resolve("cran.all.1400"));
        Map<Integer, Record> queries = readRecords(directory.resolve("cran.qry"));
        Map<Integer, Map<Integer, Integer>> judgments = readJudgments(directory.resolve("cranqrel"));
        if (documents.isEmpty() || queries.isEmpty() || judgments.isEmpty()) {
            throw new IllegalArgumentException("Cranfield inputs must not be empty");
        }
        if (judgments.size() != queries.size()) {
            throw new IllegalArgumentException("Every query must have relevance judgments");
        }
        for (int ordinal = 1; ordinal <= queries.size(); ordinal++) {
            if (!judgments.containsKey(ordinal)) {
                throw new IllegalArgumentException("Missing judgments for query ordinal " + ordinal);
            }
        }
        for (var queryJudgments : judgments.values()) {
            for (int document : queryJudgments.keySet()) {
                if (!documents.containsKey(document)) {
                    throw new IllegalArgumentException("Judgment references unknown document " + document);
                }
            }
        }

        Bm25Index titleOnly = new Bm25Index();
        Bm25Index titleAndBody = new Bm25Index();
        for (var entry : documents.entrySet()) {
            String id = Integer.toString(entry.getKey());
            String title = entry.getValue().title();
            String body = entry.getValue().body();
            String url = "https://cranfield.example/doc/" + id;
            titleOnly.add(new IndexedDocument(id, url, title, "", Instant.EPOCH));
            titleAndBody.add(new IndexedDocument(id, url, title, body, Instant.EPOCH));
        }

        System.out.printf("dataset=cranfield1400 documents=%d queries=%d judged_queries=%d%n",
                documents.size(), queries.size(), judgments.size());
        evaluate(titleOnly, queries, judgments).print("title_only");
        evaluate(titleAndBody, queries, judgments).print("title_and_abstract");
    }

    private static Map<Integer, Record> readRecords(Path path) throws IOException {
        Map<Integer, Record> records = new TreeMap<>();
        Record current = null;
        String section = "";
        for (String line : Files.readAllLines(path, StandardCharsets.ISO_8859_1)) {
            if (line.startsWith(".I ")) {
                int id = Integer.parseInt(line.substring(3).trim());
                current = new Record();
                if (records.putIfAbsent(id, current) != null) {
                    throw new IllegalArgumentException("Duplicate record ID: " + id);
                }
                section = "";
            } else if (line.matches("\\.[A-Z]")) {
                section = line;
            } else if (current != null && (section.equals(".T") || section.equals(".W"))) {
                StringBuilder target = section.equals(".T") ? current.title : current.body;
                target.append(line).append(' ');
            }
        }
        return records;
    }

    private static Map<Integer, Map<Integer, Integer>> readJudgments(Path path) throws IOException {
        Map<Integer, Map<Integer, Integer>> judgments = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.US_ASCII)) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Invalid qrels line: " + line);
            int query = Integer.parseInt(parts[0]);
            int document = Integer.parseInt(parts[1]);
            int grade = Integer.parseInt(parts[2]);
            // Cranfield uses an inverted scale: -1 is strongest, then 1 through
            // 4 descend to minimum interest. Convert to positive gain 5..1.
            if (grade != -1 && (grade < 1 || grade > 4)) {
                throw new IllegalArgumentException("Invalid relevance grade");
            }
            int gain = grade == -1 ? 5 : 5 - grade;
            judgments.computeIfAbsent(query, ignored -> new HashMap<>())
                    .merge(document, gain, Math::max);
        }
        return judgments;
    }

    private static Scores evaluate(Bm25Index index, Map<Integer, Record> queries,
                                   Map<Integer, Map<Integer, Integer>> judgments) {
        int count = 0;
        double hits = 0, mrr = 0, recall = 0, ndcg = 0;
        // The Glasgow qrels number questions by their order in cran.qry, while
        // the .I labels in that file have gaps. Joining on .I silently loses cases.
        List<Record> orderedQueries = new ArrayList<>(queries.values());
        for (int ordinal = 1; ordinal <= orderedQueries.size(); ordinal++) {
            Map<Integer, Integer> relevant = judgments.get(ordinal);
            if (relevant == null) continue;
            long relevantCount = relevant.size();
            if (relevantCount == 0) continue;
            List<SearchHit> results = index.search(orderedQueries.get(ordinal - 1).body(), 10);
            int found = 0;
            double dcg = 0;
            double reciprocalRank = 0;
            for (int rank = 0; rank < results.size(); rank++) {
                int id = Integer.parseInt(results.get(rank).id());
                int gain = relevant.getOrDefault(id, 0);
                if (gain > 0) {
                    found++;
                    if (reciprocalRank == 0) reciprocalRank = 1.0 / (rank + 1);
                }
                dcg += gain / log2(rank + 2);
            }
            List<Integer> idealGrades = new ArrayList<>(relevant.values());
            idealGrades.sort((a, b) -> Integer.compare(b, a));
            double ideal = 0;
            for (int rank = 0; rank < Math.min(10, idealGrades.size()); rank++) {
                ideal += idealGrades.get(rank) / log2(rank + 2);
            }
            count++;
            hits += found > 0 ? 1 : 0;
            mrr += reciprocalRank;
            recall += (double) found / relevantCount;
            ndcg += ideal == 0 ? 0 : dcg / ideal;
        }
        if (count == 0) throw new IllegalArgumentException("No judged queries to evaluate");
        return new Scores(count, hits, mrr, recall, ndcg);
    }

    private static double log2(int value) {
        return Math.log(value) / Math.log(2);
    }
}
