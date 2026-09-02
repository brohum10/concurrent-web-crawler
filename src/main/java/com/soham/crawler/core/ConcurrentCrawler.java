package com.soham.crawler.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
        return crawl(
                seed,
                new CrawlOptions(maxPages, 10, CrawlScope.SAME_HOST),
                () -> false,
                ignored -> {},
                pageConsumer);
    }

    public CrawlReport crawl(
            URI seed,
            CrawlOptions options,
            BooleanSupplier cancelled,
            Consumer<CrawlProgress> progressConsumer,
            Consumer<FetchedPage> pageConsumer) {
        URI canonicalSeed = UrlCanonicalizer.canonicalize(seed)
                .orElseThrow(() -> new IllegalArgumentException("seed must be an absolute HTTP(S) URL"));
        if (!safetyPolicy.isAllowed(canonicalSeed)) {
            throw new IllegalArgumentException("seed resolves to a blocked or private address");
        }

        long started = System.nanoTime();
        LinkedBlockingQueue<CrawlTask> frontier = new LinkedBlockingQueue<>();
        Set<URI> scheduledUrls = ConcurrentHashMap.newKeySet();
        AtomicInteger scheduled = new AtomicInteger(1);
        AtomicInteger outstanding = new AtomicInteger(1);
        AtomicInteger indexed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger maxDepthReached = new AtomicInteger();
        AtomicLong bytesProcessed = new AtomicLong();
        frontier.add(new CrawlTask(canonicalSeed, 0));
        scheduledUrls.add(canonicalSeed);

        var executor = java.util.concurrent.Executors.newFixedThreadPool(workers);
        for (int worker = 0; worker < workers; worker++) {
            executor.submit(() -> {
                while (outstanding.get() > 0) {
                    CrawlTask task;
                    try {
                        task = frontier.poll(50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (task == null) {
                        continue;
                    }
                    try {
                        if (cancelled.getAsBoolean()) {
                            skipped.incrementAndGet();
                            continue;
                        }
                        maxDepthReached.accumulateAndGet(task.depth(), Math::max);
                        if (!robotsPolicy.isAllowed(task.url())) {
                            skipped.incrementAndGet();
                            continue;
                        }

                        rateLimiter.acquire(task.url());
                        FetchedPage page = fetcher.fetch(task.url());
                        pageConsumer.accept(page);
                        indexed.incrementAndGet();
                        bytesProcessed.addAndGet(page.content().getBytes(StandardCharsets.UTF_8).length);

                        if (task.depth() >= options.maxDepth()) {
                            continue;
                        }
                        for (URI link : page.links()) {
                            schedule(
                                    canonicalSeed,
                                    link,
                                    task.depth() + 1,
                                    options,
                                    safetyPolicy,
                                    scheduledUrls,
                                    scheduled,
                                    outstanding,
                                    skipped,
                                    frontier);
                        }
                    } catch (Exception exception) {
                        failed.incrementAndGet();
                    } finally {
                        outstanding.decrementAndGet();
                        publishProgress(
                                progressConsumer,
                                new CrawlProgress(
                                        scheduled.get(),
                                        indexed.get(),
                                        failed.get(),
                                        skipped.get(),
                                        bytesProcessed.get(),
                                        maxDepthReached.get()));
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
        return new CrawlReport(
                scheduled.get(),
                indexed.get(),
                failed.get(),
                skipped.get(),
                bytesProcessed.get(),
                maxDepthReached.get(),
                cancelled.getAsBoolean(),
                Duration.ofNanos(System.nanoTime() - started));
    }

    private static void schedule(
            URI seed,
            URI discovered,
            int depth,
            CrawlOptions options,
            HostSafetyPolicy safetyPolicy,
            Set<URI> scheduledUrls,
            AtomicInteger scheduled,
            AtomicInteger outstanding,
            AtomicInteger skipped,
            LinkedBlockingQueue<CrawlTask> frontier) {
        var canonical = UrlCanonicalizer.canonicalize(discovered);
        if (canonical.isEmpty()) {
            skipped.incrementAndGet();
            return;
        }
        URI next = canonical.get();
        if (!options.scope().allows(seed, next)
                || !CrawlUrlPolicy.isLikelyHtml(next)
                || !safetyPolicy.isAllowed(next)) {
            skipped.incrementAndGet();
            return;
        }
        if (!scheduledUrls.add(next)) {
            return;
        }
        int reserved = scheduled.incrementAndGet();
        if (reserved > options.maxPages()) {
            scheduled.decrementAndGet();
            scheduledUrls.remove(next);
            return;
        }
        outstanding.incrementAndGet();
        frontier.add(new CrawlTask(next, depth));
    }

    private static void publishProgress(Consumer<CrawlProgress> consumer, CrawlProgress progress) {
        try {
            consumer.accept(progress);
        } catch (RuntimeException ignored) {
            // A progress observer should never be able to stop the crawl.
        }
    }

    private record CrawlTask(URI url, int depth) {}
}
