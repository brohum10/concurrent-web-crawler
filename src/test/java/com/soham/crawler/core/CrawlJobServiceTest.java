package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CrawlJobServiceTest {
    @Test
    void rejectsWorkWhenTheBoundedQueueIsFull() throws Exception {
        CrawlerService crawler = mock(CrawlerService.class);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(crawler.crawl(anyString(), any(), any(), any())).thenAnswer(ignored -> {
            running.countDown();
            release.await(2, TimeUnit.SECONDS);
            return new CrawlReport(1, 1, 0, 0, 10, 0, false, Duration.ofMillis(10));
        });
        CrawlerProperties properties = new CrawlerProperties(
                1,
                10,
                Duration.ZERO,
                Duration.ofSeconds(1),
                0,
                1_024,
                1,
                1,
                "test-agent");
        CrawlJobService service = new CrawlJobService(crawler, properties, new SimpleMeterRegistry());
        CrawlOptions options = new CrawlOptions(1, 0, CrawlScope.SAME_ORIGIN);

        try {
            service.start("https://example.com", options);
            running.await(1, TimeUnit.SECONDS);
            service.start("https://example.com", options);

            assertThrows(RejectedExecutionException.class, () -> service.start("https://example.com", options));
        } finally {
            release.countDown();
            service.shutdown();
        }
    }
}
