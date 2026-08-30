package com.soham.crawler.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HttpPageFetcher implements PageFetcher {
    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;
    private final int maxRetries;

    public HttpPageFetcher(HttpClient client, Duration timeout, String userAgent, int maxRetries) {
        this.client = client;
        this.timeout = timeout;
        this.userAgent = userAgent;
        this.maxRetries = maxRetries;
    }

    @Override
    public FetchedPage fetch(URI url) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(url)
                        .timeout(timeout)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (response.statusCode() < 200 || response.statusCode() >= 300 || !contentType.contains("text/html")) {
                    throw new IOException("Unexpected response " + response.statusCode() + " for " + url);
                }
                Document document = Jsoup.parse(response.body(), url.toString());
                List<URI> links = document.select("a[href]").stream()
                        .map(element -> element.absUrl("href"))
                        .filter(link -> !link.isBlank())
                        .map(URI::create)
                        .toList();
                return new FetchedPage(url, document.title(), document.body().text(), links);
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt < maxRetries) {
                    Thread.sleep(Math.min(1_000L, 100L << attempt));
                }
            }
        }
        throw lastFailure == null ? new IOException("Unable to fetch " + url) : lastFailure;
    }
}
