package org.kiwiproject.dropwizard.util.job;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Factory for a few simple {@link JobErrorHandler} implementations.
 */
@UtilityClass
public class JobErrorHandlers {

    private static final String LOG_MESSAGE_TEMPLATE = "Job '{}' threw an exception";

    /**
     * A no-op error handler.
     *
     * @return a {@link JobErrorHandler} that does nothing at all
     */
    public static JobErrorHandler noOpHandler() {
        return new NoOpJobErrorHandler();
    }

    /**
     * An error handler that logs the job name and exception at WARN level.
     *
     * @return a {@link JobErrorHandler} that logs errors
     */
    public static JobErrorHandler loggingHandler() {
        return new LoggingJobErrorHandler();
    }

    /**
     * An error handler that logs the job name and exception at WARN level using the given {@link Logger}.
     * <p>
     * Prefer this over {@link #loggingHandler()} when you want or need control of the {@link Logger} to use.
     *
     * @param logger the SLF4J logger to log with
     * @return a {@link JobErrorHandler} that logs errors
     */
    public static JobErrorHandler loggingHandler(Logger logger) {
        return new CustomLoggerJobErrorHandler(logger);
    }

    private static class NoOpJobErrorHandler implements JobErrorHandler {
        @Override
        public void handle(MonitoredJob job, Exception exception) {
            // no-op
        }
    }

    @Slf4j
    private static class LoggingJobErrorHandler implements JobErrorHandler {
        @Override
        public void handle(MonitoredJob job, Exception exception) {
            LOG.warn(LOG_MESSAGE_TEMPLATE, job.getName(), exception);
        }
    }

    private record CustomLoggerJobErrorHandler(Logger customLogger) implements JobErrorHandler {

        @Override
        public void handle(MonitoredJob job, Exception exception) {
            customLogger.warn(LOG_MESSAGE_TEMPLATE, job.getName(), exception);
        }
    }
}
