package com.soham.crawler.core;

import java.net.URI;
import java.util.List;

public record FetchedPage(URI url, String title, String content, List<URI> links) {
    public FetchedPage {
        links = List.copyOf(links);
    }
}
