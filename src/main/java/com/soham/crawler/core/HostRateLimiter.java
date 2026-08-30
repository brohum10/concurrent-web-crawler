package com.soham.crawler.core;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class HostRateLimiter {
    private final long delayNanos;
    private final ConcurrentHashMap<String, AtomicLong> nextAllowedByHost = new ConcurrentHashMap<>();

    public HostRateLimiter(Duration delay) {
        this.delayNanos = Math.max(0, delay.toNanos());
    }

    public void acquire(URI uri) {
        if (delayNanos == 0) {
            return;
        }
        AtomicLong nextAllowed = nextAllowedByHost.computeIfAbsent(uri.getHost(), ignored -> new AtomicLong());
        while (true) {
            long now = System.nanoTime();
            long previous = nextAllowed.get();
            long slot = Math.max(now, previous);
            if (nextAllowed.compareAndSet(previous, slot + delayNanos)) {
                LockSupport.parkNanos(Math.max(0, slot - now));
                return;
            }
        }
    }
}
