package com.soham.crawler.core;

import java.net.URI;

@FunctionalInterface
public interface RobotsPolicy {
    boolean isAllowed(URI uri);
}
