package com.soham.crawler.core;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConcurrentCrawler {
    private final PageFetcher fetcher;
    private final RobotsPolicy robotsPolicy;
    private final HostSafetyPolicy safetyPolicy;
    private final HostRateLimiter rateLimiter;
    private final int workers;

    public ConcurrentCrawler(PageFetcher fetcher, RobotsPolicy robotsPolicy, HostSafetyPolicy safetyPolicy,
            HostRateLimiter rateLimiter, int workers) {
        this.fetcher = fetcher;
        this.robotsPolicy = robotsPolicy;
        this.safetyPolicy = safetyPolicy;
        this.rateLimiter = rateLimiter;
        this.workers = workers;
    }

    public CrawlReport crawl(URI seed, int maxPages, Consumer<FetchedPage> pageConsumer) {
        URI canonicalSeed = UrlCanonicalizer.canonicalize(seed)
                .orElseThrow(() -> new IllegalArgumentException("seed must be an absolute HTTP(S) URL"));
        if (!safetyPolicy.isAllowed(canonicalSeed)) {
            throw new IllegalArgumentException("seed resolves to a blocked or private address");
        }
        long started = System.nanoTime();
        LinkedBlockingQueue<URI> frontier = new LinkedBlockingQueue<>();
        Set<URI> scheduled = ConcurrentHashMap.newKeySet();
        AtomicInteger outstanding = new AtomicInteger(1);
        AtomicInteger indexed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        frontier.add(canonicalSeed);
        scheduled.add(canonicalSeed);

        var executor = java.util.concurrent.Executors.newFixedThreadPool(workers);
        for (int worker = 0; worker < workers; worker++) {
            executor.submit(() -> {
                while (outstanding.get() > 0) {
                    URI current;
                    try {
                        current = frontier.poll(50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (current == null) {
                        continue;
                    }
                    try {
                        if (!robotsPolicy.isAllowed(current)) {
                            continue;
                        }
                        rateLimiter.acquire(current);
                        FetchedPage page = fetcher.fetch(current);
                        pageConsumer.accept(page);
                        indexed.incrementAndGet();
                        for (URI link : page.links()) {
                            if (scheduled.size() >= maxPages) {
                                break;
                            }
                            UrlCanonicalizer.canonicalize(link)
                                    .filter(safetyPolicy::isAllowed)
                                    .filter(scheduled::add)
                                    .ifPresent(next -> {
                                        outstanding.incrementAndGet();
                                        frontier.add(next);
                                    });
                        }
                    } catch (Exception exception) {
                        failed.incrementAndGet();
                    } finally {
                        outstanding.decrementAndGet();
                    }
                }
            });
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        return new CrawlReport(scheduled.size(), indexed.get(), failed.get(), Duration.ofNanos(System.nanoTime() - started));
    }
}
