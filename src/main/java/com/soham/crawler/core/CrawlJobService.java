package com.soham.crawler.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class CrawlJobService {
    private static final int MAX_RETAINED_JOBS = 200;

    private final CrawlerService crawlerService;
    private final ExecutorService executor;
    private final MeterRegistry meterRegistry;
    private final Counter jobsStarted;
    private final Timer jobDuration;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public CrawlJobService(CrawlerService crawlerService, CrawlerProperties properties, MeterRegistry meterRegistry) {
        this.crawlerService = crawlerService;
        this.meterRegistry = meterRegistry;
        this.executor = new ThreadPoolExecutor(
                properties.maxConcurrentJobs(),
                properties.maxConcurrentJobs(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.maxQueuedJobs()),
                runnable -> {
                    Thread thread = new Thread(runnable, "crawl-job-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.jobsStarted = Counter.builder("crawler.jobs.started")
                .description("Crawl jobs accepted by the API")
                .register(meterRegistry);
        this.jobDuration = Timer.builder("crawler.jobs.duration")
                .description("Active crawl execution duration")
                .register(meterRegistry);
    }

    public CrawlJobSnapshot start(String seed, CrawlOptions options) {
        pruneFinishedJobs();
        Job job = new Job("crawl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), seed, options);
        jobs.put(job.id, job);
        try {
            executor.execute(() -> run(job));
            jobsStarted.increment();
        } catch (RuntimeException exception) {
            jobs.remove(job.id);
            throw exception;
        }
        return job.snapshot();
    }

    public CrawlJobSnapshot get(String id) {
        return requireJob(id).snapshot();
    }

    public List<CrawlJobSnapshot> list() {
        return jobs.values().stream()
                .map(Job::snapshot)
                .sorted(Comparator.comparing(CrawlJobSnapshot::createdAt).reversed())
                .toList();
    }

    public CrawlJobSnapshot cancel(String id) {
        Job job = requireJob(id);
        job.cancel();
        return job.snapshot();
    }

    public Map<CrawlJobStatus, Long> countsByStatus() {
        Map<CrawlJobStatus, Long> counts = new EnumMap<>(CrawlJobStatus.class);
        for (CrawlJobStatus status : CrawlJobStatus.values()) {
            counts.put(status, 0L);
        }
        jobs.values().forEach(job -> counts.compute(job.status, (ignored, count) -> count + 1));
        return Map.copyOf(counts);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void run(Job job) {
        if (!job.begin()) {
            recordCompletion(job.status);
            return;
        }
        long started = System.nanoTime();
        try {
            CrawlReport report = crawlerService.crawl(
                    job.seed,
                    job.options,
                    job.cancelled::get,
                    progress -> job.progress = progress);
            job.complete(report);
        } catch (Exception exception) {
            job.fail(exception);
        } finally {
            jobDuration.record(Duration.ofNanos(System.nanoTime() - started));
            recordCompletion(job.status);
        }
    }

    private void recordCompletion(CrawlJobStatus status) {
        Counter.builder("crawler.jobs.completed")
                .description("Completed crawl jobs by terminal status")
                .tag("status", status.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }

    private Job requireJob(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new NoSuchElementException("crawl job not found: " + id);
        }
        return job;
    }

    private void pruneFinishedJobs() {
        int excess = jobs.size() - MAX_RETAINED_JOBS + 1;
        if (excess <= 0) {
            return;
        }
        jobs.values().stream()
                .filter(Job::isTerminal)
                .sorted(Comparator.comparing(job -> job.createdAt))
                .limit(excess)
                .forEach(job -> jobs.remove(job.id, job));
    }

    private static final class Job {
        private final String id;
        private final String seed;
        private final CrawlOptions options;
        private final Instant createdAt = Instant.now();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile CrawlJobStatus status = CrawlJobStatus.QUEUED;
        private volatile CrawlProgress progress = CrawlProgress.empty();
        private volatile CrawlReport report;
        private volatile String error;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        private Job(String id, String seed, CrawlOptions options) {
            this.id = id;
            this.seed = seed;
            this.options = options;
        }

        private synchronized boolean begin() {
            if (cancelled.get()) {
                status = CrawlJobStatus.CANCELLED;
                finishedAt = Instant.now();
                return false;
            }
            status = CrawlJobStatus.RUNNING;
            startedAt = Instant.now();
            return true;
        }

        private synchronized void cancel() {
            if (status == CrawlJobStatus.SUCCEEDED
                    || status == CrawlJobStatus.FAILED
                    || status == CrawlJobStatus.CANCELLED) {
                return;
            }
            cancelled.set(true);
            if (status == CrawlJobStatus.QUEUED) {
                status = CrawlJobStatus.CANCELLED;
                finishedAt = Instant.now();
            }
        }

        private synchronized void complete(CrawlReport crawlReport) {
            report = crawlReport;
            progress = new CrawlProgress(
                    crawlReport.scheduled(),
                    crawlReport.indexed(),
                    crawlReport.failed(),
                    crawlReport.skipped(),
                    crawlReport.bytesProcessed(),
                    crawlReport.maxDepthReached());
            status = crawlReport.cancelled() ? CrawlJobStatus.CANCELLED : CrawlJobStatus.SUCCEEDED;
            finishedAt = Instant.now();
        }

        private synchronized void fail(Exception exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            status = cancelled.get() ? CrawlJobStatus.CANCELLED : CrawlJobStatus.FAILED;
            finishedAt = Instant.now();
        }

        private CrawlJobSnapshot snapshot() {
            return new CrawlJobSnapshot(
                    id, status, seed, options, progress, report, error, createdAt, startedAt, finishedAt);
        }

        private boolean isTerminal() {
            return status == CrawlJobStatus.SUCCEEDED
                    || status == CrawlJobStatus.FAILED
                    || status == CrawlJobStatus.CANCELLED;
        }
    }
}
