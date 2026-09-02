package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CrawlScopeTest {
    private static final URI SEED = URI.create("https://example.com/start");

    @Test
    void sameOriginRequiresMatchingSchemeHostAndPort() {
        assertTrue(CrawlScope.SAME_ORIGIN.allows(SEED, URI.create("https://example.com/docs")));
        assertFalse(CrawlScope.SAME_ORIGIN.allows(SEED, URI.create("http://example.com/docs")));
        assertFalse(CrawlScope.SAME_ORIGIN.allows(SEED, URI.create("https://example.com:8443/docs")));
    }

    @Test
    void sameHostAllowsSchemeChangesButNotExternalHosts() {
        assertTrue(CrawlScope.SAME_HOST.allows(SEED, URI.create("http://example.com/docs")));
        assertFalse(CrawlScope.SAME_HOST.allows(SEED, URI.create("https://other.example/docs")));
        assertTrue(CrawlScope.ANY.allows(SEED, URI.create("https://other.example/docs")));
    }
}
