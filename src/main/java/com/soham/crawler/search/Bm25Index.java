package com.soham.crawler.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class Bm25Index {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{Nd}]+");

    private final Map<String, IndexedDocument> documents = new HashMap<>();
    private final Map<String, Integer> documentLengths = new HashMap<>();
    private final Map<String, Map<String, Integer>> postings = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long totalDocumentLength;

    public void add(IndexedDocument document) {
        Map<String, Integer> frequencies = termFrequencies(document.title() + " " + document.content());
        lock.writeLock().lock();
        try {
            removeIfPresent(document.id());
            documents.put(document.id(), document);
            int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            documentLengths.put(document.id(), length);
            totalDocumentLength += length;
            frequencies.forEach((term, count) -> postings
                    .computeIfAbsent(term, ignored -> new HashMap<>())
                    .put(document.id(), count));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<SearchHit> search(String query, int limit) {
        if (query == null || query.isBlank() || limit < 1) {
            return List.of();
        }
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        lock.readLock().lock();
        try {
            if (documents.isEmpty()) {
                return List.of();
            }
            double averageLength = Math.max(1.0, (double) totalDocumentLength / documents.size());
            Map<String, Double> scores = new HashMap<>();
            for (String term : queryTerms) {
                Map<String, Integer> matches = postings.get(term);
                if (matches == null) {
                    continue;
                }
                double idf = Math.log(1.0 + (documents.size() - matches.size() + 0.5) / (matches.size() + 0.5));
                matches.forEach((documentId, frequency) -> {
                    int length = documentLengths.get(documentId);
                    double denominator = frequency + K1 * (1.0 - B + B * length / averageLength);
                    double score = idf * frequency * (K1 + 1.0) / denominator;
                    scores.merge(documentId, score, Double::sum);
                });
            }
            PriorityQueue<SearchHit> top = new PriorityQueue<>(Comparator.comparingDouble(SearchHit::score));
            scores.forEach((id, score) -> {
                IndexedDocument document = documents.get(id);
                SearchHit hit = new SearchHit(
                        id, document.url(), document.title(), snippet(document.content()), score);
                top.offer(hit);
                if (top.size() > limit) {
                    top.poll();
                }
            });
            List<SearchHit> results = new ArrayList<>(top);
            results.sort(Comparator.comparingDouble(SearchHit::score).reversed());
            return List.copyOf(results);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void removeIfPresent(String id) {
        IndexedDocument previous = documents.remove(id);
        if (previous == null) {
            return;
        }
        totalDocumentLength -= documentLengths.remove(id);
        postings.values().forEach(ids -> ids.remove(id));
        postings.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> frequencies = new HashMap<>();
        tokenize(text).forEach(term -> frequencies.merge(term, 1, Integer::sum));
        return frequencies;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static String snippet(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 177) + "...";
    }
}
