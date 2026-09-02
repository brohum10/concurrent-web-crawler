package com.soham.crawler.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HttpPageFetcher implements PageFetcher {
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;
    private final int maxRetries;
    private final int maxResponseBytes;
    private final HostSafetyPolicy safetyPolicy;

    public HttpPageFetcher(
            HttpClient client,
            Duration timeout,
            String userAgent,
            int maxRetries,
            int maxResponseBytes,
            HostSafetyPolicy safetyPolicy) {
        this.client = client;
        this.timeout = timeout;
        this.userAgent = userAgent;
        this.maxRetries = maxRetries;
        this.maxResponseBytes = maxResponseBytes;
        this.safetyPolicy = safetyPolicy;
    }

    @Override
    public FetchedPage fetch(URI url) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return fetchFollowingSafeRedirects(url);
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt < maxRetries) {
                    Thread.sleep(Math.min(1_000L, 100L << attempt));
                }
            }
        }
        throw lastFailure == null ? new IOException("Unable to fetch " + url) : lastFailure;
    }

    private FetchedPage fetchFollowingSafeRedirects(URI original) throws IOException, InterruptedException {
        URI current = original;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            current = UrlCanonicalizer.canonicalize(current)
                    .orElseThrow(() -> new IOException("Invalid redirect URL"));
            if (!safetyPolicy.isAllowed(current)) {
                throw new IOException("Redirect target resolves to a blocked or private address");
            }

            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                try (InputStream ignored = response.body()) {
                    String location = response.headers()
                            .firstValue("Location")
                            .orElseThrow(() -> new IOException("Redirect response is missing Location"));
                    current = current.resolve(location);
                }
                continue;
            }
            if (status < 200 || status >= 300) {
                try (InputStream ignored = response.body()) {
                    throw new IOException("Unexpected response " + status + " for " + current);
                }
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml")) {
                try (InputStream ignored = response.body()) {
                    throw new IOException("Unsupported Content-Type for " + current);
                }
            }

            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = body.readNBytes(maxResponseBytes + 1);
            }
            if (bytes.length > maxResponseBytes) {
                throw new IOException("Response exceeded the " + maxResponseBytes + " byte limit");
            }
            Document document = Jsoup.parse(new ByteArrayInputStream(bytes), null, current.toString());
            List<URI> links = document.select("a[href]").stream()
                    .map(element -> element.absUrl("href"))
                    .map(HttpPageFetcher::parseUri)
                    .flatMap(Optional::stream)
                    .toList();
            String content = document.body() == null ? "" : document.body().text();
            return new FetchedPage(current, document.title(), content, links);
        }
        throw new IOException("Too many redirects for " + original);
    }

    private static Optional<URI> parseUri(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
