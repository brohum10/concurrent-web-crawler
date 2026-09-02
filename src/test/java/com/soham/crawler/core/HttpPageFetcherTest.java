package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpPageFetcherTest {
    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void followsRedirectsAndReturnsTheFinalCanonicalUrl() throws Exception {
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "<html><title>Final</title><body>Hello <a href='/next'>next</a></body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        FetchedPage page = fetcher(1_000).fetch(baseUrl.resolve("/start"));

        assertEquals(baseUrl.resolve("/final"), page.url());
        assertEquals("Final", page.title());
        assertEquals(baseUrl.resolve("/next"), page.links().get(0));
    }

    @Test
    void rejectsResponsesAboveTheConfiguredLimit() {
        server.createContext("/large", exchange -> {
            byte[] body = "<html><body>This response is intentionally too large.</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThrows(IOException.class, () -> fetcher(16).fetch(baseUrl.resolve("/large")));
    }

    private static HttpPageFetcher fetcher(int maxBytes) {
        HostSafetyPolicy allowFixtureHost = new HostSafetyPolicy() {
            @Override
            public boolean isAllowed(URI ignored) {
                return true;
            }
        };
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return new HttpPageFetcher(client, Duration.ofSeconds(2), "test-agent", 0, maxBytes, allowFixtureHost);
    }
}
