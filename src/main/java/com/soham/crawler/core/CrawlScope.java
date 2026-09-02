package com.soham.crawler.core;

import java.net.URI;

public enum CrawlScope {
    SAME_ORIGIN {
        @Override
        public boolean allows(URI seed, URI candidate) {
            return seed.getScheme().equalsIgnoreCase(candidate.getScheme())
                    && seed.getHost().equalsIgnoreCase(candidate.getHost())
                    && effectivePort(seed) == effectivePort(candidate);
        }
    },
    SAME_HOST {
        @Override
        public boolean allows(URI seed, URI candidate) {
            return seed.getHost().equalsIgnoreCase(candidate.getHost());
        }
    },
    ANY {
        @Override
        public boolean allows(URI seed, URI candidate) {
            return true;
        }
    };

    public abstract boolean allows(URI seed, URI candidate);

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}

