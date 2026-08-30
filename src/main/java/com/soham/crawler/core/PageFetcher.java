package com.soham.crawler.core;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface PageFetcher {
    FetchedPage fetch(URI url) throws IOException, InterruptedException;
}
