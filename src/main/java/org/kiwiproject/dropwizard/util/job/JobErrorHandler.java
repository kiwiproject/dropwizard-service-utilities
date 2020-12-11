package org.kiwiproject.dropwizard.util.job;

/**
 * Interface for defining a handler when the {@link MonitoredJob} encounters an error.
 */
public interface JobErrorHandler {

    /**
     * Handles a {@link Throwable} that occurred during the run of the {@link MonitoredJob}.
     *
     * @param job       the job that was run
     * @param throwable the error that occurred
     */
    void handle(MonitoredJob job, Throwable throwable);
}
