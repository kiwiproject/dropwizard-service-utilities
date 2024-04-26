package org.kiwiproject.dropwizard.util.bundle;

import org.kiwiproject.net.LocalPortChecker;

import java.util.concurrent.atomic.AtomicInteger;

class CountingLocalPortChecker extends LocalPortChecker {

    final AtomicInteger portCheckCount = new AtomicInteger();

    @Override
    public boolean isPortAvailable(int port) {
        portCheckCount.incrementAndGet();
        return super.isPortAvailable(port);
    }
}
