package com.soham.crawler.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RobotsTxtPolicy implements RobotsPolicy {
    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;
    private final Map<String, List<String>> disallowedPaths = new ConcurrentHashMap<>();

    public RobotsTxtPolicy(HttpClient client, Duration timeout, String userAgent) {
        this.client = client;
        this.timeout = timeout;
        this.userAgent = userAgent;
    }

    @Override
    public boolean isAllowed(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        List<String> rules = disallowedPaths.computeIfAbsent(origin, this::loadRules);
        String path = uri.getPath().isBlank() ? "/" : uri.getPath();
        return rules.stream().noneMatch(rule -> !rule.isBlank() && path.startsWith(rule));
    }

    private List<String> loadRules(String origin) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            return parseWildcardRules(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    static List<String> parseWildcardRules(String body) {
        List<String> rules = new ArrayList<>();
        boolean wildcardSection = false;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.regionMatches(true, 0, "User-agent:", 0, 11)) {
                wildcardSection = line.substring(11).trim().equals("*");
            } else if (wildcardSection && line.regionMatches(true, 0, "Disallow:", 0, 9)) {
                String path = line.substring(9).trim();
                if (!path.isBlank()) {
                    rules.add(path);
                }
            }
        }
        return List.copyOf(rules);
    }
}
