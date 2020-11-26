package org.kiwiproject.dropwizard.util.startup;

import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around {@link System#exit(int)} to allow for unit testing scenarios in which we need to exit the JVM, for
 * example when the Jetty server cannot start due to failure to obtain a port, or some other non-recoverable startup
 * error.
 */
@Slf4j
public class SystemExecutioner {

    public void exit() {
        LOG.warn("Terminating the VM!");
        System.exit(1);
    }
}
