package org.kiwiproject.dropwizard.util.startup;

import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around {@link System#exit(int)} to allow for unit testing scenarios in which we need to exit the JVM, for
 * example when the Jetty server cannot start due to failure to obtain a port, or some other non-recoverable startup
 * error.
 */
@Slf4j
public class SystemExecutioner {

    void exit() {
        LOG.warn("Terminating the VM!");
        // TODO: Should we wait some number of seconds as a daemon thread before shutting ourselves down to see if we actually
        //       die?  How to do that??
        System.exit(1);
    }
}
