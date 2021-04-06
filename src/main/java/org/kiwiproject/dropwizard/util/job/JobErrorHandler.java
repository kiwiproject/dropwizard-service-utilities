package org.kiwiproject.dropwizard.util.job;

/**
 * Interface that defines a handler for when a {@link MonitoredJob} encounters an error.
 */
public interface JobErrorHandler {

    /**
     * Handles an {@link Exception} that occurred during the run of the {@link MonitoredJob}.
     *
     * @param job       the job that was run
     * @param exception the error that occurred
     */
    void handle(MonitoredJob job, Exception exception);
}
