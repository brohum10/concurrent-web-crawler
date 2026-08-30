package com.soham.crawler.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public final class UrlCanonicalizer {
    private UrlCanonicalizer() {}

    public static Optional<URI> canonicalize(URI input) {
        if (input == null || input.getScheme() == null || input.getHost() == null) {
            return Optional.empty();
        }
        String scheme = input.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }
        String host = input.getHost().toLowerCase(Locale.ROOT);
        int port = input.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }
        String path = input.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            return Optional.of(new URI(scheme, input.getUserInfo(), host, port, path, input.getQuery(), null).normalize());
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }
}
