package com.soham.crawler.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class UrlCanonicalizer {
    private UrlCanonicalizer() {}

    public static Optional<URI> canonicalize(URI input) {
        if (input == null || input.getScheme() == null || input.getHost() == null || input.getUserInfo() != null) {
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
            URI base = new URI(scheme, null, host, port, path, null, null).normalize();
            String query = canonicalQuery(input.getRawQuery());
            return Optional.of(query == null ? base : URI.create(base.toASCIIString() + "?" + query));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String[] parameters = Arrays.stream(rawQuery.split("&"))
                .filter(parameter -> !parameter.isBlank())
                .filter(parameter -> !isTrackingParameter(parameter))
                .sorted()
                .toArray(String[]::new);
        return parameters.length == 0 ? null : String.join("&", parameters);
    }

    private static boolean isTrackingParameter(String parameter) {
        String name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
        return name.startsWith("utm_")
                || name.equals("fbclid")
                || name.equals("gclid")
                || name.equals("dclid")
                || name.equals("mc_cid")
                || name.equals("mc_eid");
    }
}
