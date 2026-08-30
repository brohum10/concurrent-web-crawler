package com.soham.crawler.api;

import com.soham.crawler.core.CrawlReport;
import com.soham.crawler.core.CrawlerService;
import com.soham.crawler.search.SearchHit;
import com.soham.crawler.search.SearchService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final SearchService searchService;

    public CrawlerController(CrawlerService crawlerService, SearchService searchService) {
        this.crawlerService = crawlerService;
        this.searchService = searchService;
    }

    @PostMapping("/crawl")
    public CrawlReport crawl(@RequestBody CrawlRequest request) {
        if (request.seed() == null || request.seed().isBlank()) {
            throw new IllegalArgumentException("seed is required");
        }
        return crawlerService.crawl(request.seed(), request.maxPages());
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return searchService.search(q, limit);
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

    public record CrawlRequest(String seed, Integer maxPages) {}
}
