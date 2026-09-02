package com.soham.crawler.api;

import com.soham.crawler.core.CrawlJobService;
import com.soham.crawler.core.CrawlJobSnapshot;
import com.soham.crawler.core.CrawlOptions;
import com.soham.crawler.core.CrawlReport;
import com.soham.crawler.core.CrawlScope;
import com.soham.crawler.core.CrawlerService;
import com.soham.crawler.search.SearchHit;
import com.soham.crawler.search.SearchService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CrawlerController {
    private final CrawlerService crawlerService;
    private final CrawlJobService jobService;
    private final SearchService searchService;

    public CrawlerController(CrawlerService crawlerService, CrawlJobService jobService, SearchService searchService) {
        this.crawlerService = crawlerService;
        this.jobService = jobService;
        this.searchService = searchService;
    }

    @PostMapping("/crawl")
    public CrawlReport crawl(@RequestBody CrawlRequest request) {
        validateRequest(request);
        CrawlOptions options = crawlerService.options(request.maxPages(), request.maxDepth(), request.scope());
        return crawlerService.crawl(request.seed(), options, () -> false, ignored -> {});
    }

    @PostMapping("/v2/crawls")
    public ResponseEntity<CrawlJobSnapshot> startCrawl(@RequestBody CrawlRequest request) {
        validateRequest(request);
        CrawlOptions options = crawlerService.options(request.maxPages(), request.maxDepth(), request.scope());
        return ResponseEntity.accepted().body(jobService.start(request.seed(), options));
    }

    @GetMapping("/v2/crawls")
    public List<CrawlJobSnapshot> listCrawls() {
        return jobService.list();
    }

    @GetMapping("/v2/crawls/{id}")
    public CrawlJobSnapshot getCrawl(@PathVariable String id) {
        return jobService.get(id);
    }

    @DeleteMapping("/v2/crawls/{id}")
    public CrawlJobSnapshot cancelCrawl(@PathVariable String id) {
        return jobService.cancel(id);
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return searchService.search(q, limit);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("index", searchService.stats(), "crawlJobs", jobService.countsByStatus());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "indexedDocuments", searchService.indexedCount());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> missingResource(NoSuchElementException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, String> queueFull() {
        return Map.of("error", "crawl queue is full; try again later");
    }

    private static void validateRequest(CrawlRequest request) {
        if (request == null || request.seed() == null || request.seed().isBlank()) {
            throw new IllegalArgumentException("seed is required");
        }
    }

    public record CrawlRequest(String seed, Integer maxPages, Integer maxDepth, CrawlScope scope) {}
}
