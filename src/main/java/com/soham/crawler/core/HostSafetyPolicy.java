package com.soham.crawler.core;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

public class HostSafetyPolicy {
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "localhost.localdomain");

    public boolean isAllowed(URI uri) {
        if (uri == null || uri.getHost() == null || BLOCKED_HOSTS.contains(uri.getHost().toLowerCase())) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
