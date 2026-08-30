package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {
    @Test
    void removesFragmentsDefaultPortsAndDuplicateSlashes() {
        URI canonical = UrlCanonicalizer.canonicalize(
                        URI.create("HTTPS://Example.COM:443//docs///search/#section"))
                .orElseThrow();

        assertEquals("https://example.com/docs/search", canonical.toString());
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertTrue(UrlCanonicalizer.canonicalize(URI.create("file:///etc/passwd")).isEmpty());
    }
}
