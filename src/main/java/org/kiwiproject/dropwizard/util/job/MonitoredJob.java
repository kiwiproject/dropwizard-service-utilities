package org.kiwiproject.dropwizard.util.job;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.concurrent.Async.doAsync;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.CatchingRunnable;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Sets up a job from a {@link Runnable} that can be monitored through health checks to ensure it is running correctly.
 *
 * <table>
 *     <caption>MonitoredJob properties</caption>
 *     <thead>
 *         <tr>
 *             <th>Property</th>
 *             <th>Description</th>
 *             <th>Required?</th>
 *             <th>Default</th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td>task</td>
 *             <td>A {@link Runnable} to be executed on a recurring basis</td>
 *             <td>Yes</td>
 *             <td>None</td>
 *         </tr>
 *         <tr>
 *             <td>errorHandler</td>
 *             <td>A {@link JobErrorHandler} that should be called whenever exceptions are thrown by the task</td>
 *             <td>No</td>
 *             <td>A handler that does nothing (a no-op)</td>
 *         </tr>
 *         <tr>
 *             <td>timeout</td>
 *             <td>The maximum time a job is allowed to execute before it is terminated</td>
 *             <td>No</td>
 *             <td>No timeout</td>
 *         </tr>
 *         <tr>
 *             <td>name</td>
 *             <td>A name for the job</td>
 *             <td>Yes</td>
 *             <td>None</td>
 *         </tr>
 *         <tr>
 *             <td>decisionFunction</td>
 *             <td>A {@link Predicate} that decides whether the task should run at the moment when it is called</td>
 *             <td>No</td>
 *             <td>A Predicate that always returns true</td>
 *         </tr>
 *         <tr>
 *             <td>environment</td>
 *             <td>An instance of {@link KiwiEnvironment} (mainly useful for unit testing purposes)</td>
 *             <td>No</td>
 *             <td>A {@link DefaultEnvironment} instance</td>
 *         </tr>
 *     </tbody>
 * </table>
 */
@Slf4j
public class MonitoredJob implements CatchingRunnable {

    private final Runnable task;

    private final JobErrorHandler errorHandler;

    private final Duration timeout;

    @Getter
    private final String name;

    @Getter(AccessLevel.PACKAGE)
    private final Predicate<MonitoredJob> decisionFunction;

    @Getter(AccessLevel.PACKAGE)
    private final KiwiEnvironment environment;

    /**
     * Millis since epoch when job was last successful. Will be zero if job has never run or never succeeded.
     */
    @Getter
    private final AtomicLong lastSuccess = new AtomicLong();

    /**
     * Millis since epoch when job last failed. Will be zero if job has never run or never failed.
     */
    @Getter
    private final AtomicLong lastFailure = new AtomicLong();

    /**
     * Number of times job has failed. Will be zero if job has never run or never failed.
     */
    @Getter
    private final AtomicLong failureCount = new AtomicLong();

    /**
     * Millis since epoch when job was last executed. Will be zero if job has never run.
     */
    @Getter
    private final AtomicLong lastExecutionTime = new AtomicLong();

    @Builder
    private MonitoredJob(Runnable task,
                         JobErrorHandler errorHandler,
                         Duration timeout,
                         String name,
                         Predicate<MonitoredJob> decisionFunction,
                         KiwiEnvironment environment) {
        this.name = requireNotBlank(name, "name is required");
        this.task = requireNotNull(task, "task is required");
        this.decisionFunction = isNull(decisionFunction) ? (job -> true) : decisionFunction;
        this.errorHandler = isNull(errorHandler) ? JobErrorHandlers.noOpHandler() : errorHandler;
        this.timeout = timeout;
        this.environment = isNull(environment) ? new DefaultEnvironment() : environment;
    }

    @Override
    @SneakyThrows
    public void runSafely() {
        if (isActive()) {
            LOG.debug("Executing job: {}", name);
            var startTime = environment.currentTimeMillis();

            if (nonNull(timeout)) {
                doAsync(task).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                task.run();
            }

            lastExecutionTime.set(environment.currentTimeMillis() - startTime);
            LOG.debug("Completed job: {}", name);
        } else {
            LOG.trace("Not active, skipping job: {}", name);
        }

        lastSuccess.set(environment.currentTimeMillis());
    }

    /**
     * Checks if the job should be active and execute by delegating to the decision function.
     * <p>
     * This is useful if the same job runs in separate JVMs but only a single one of the jobs should run at a time.
     * For example, suppose there are multiple instances of a service that has a data cleanup job that runs
     * occasionally, but you only want one of the active instances to actually run the cleanup job. In this
     * situation, you could provide a decision function that uses a
     * <a href="https://curator.apache.org/curator-recipes/leader-latch.html">distributed leader latch</a> to ensure
     * only the "leader" service instance runs the job. Or, you could use time-based logic and only return true when
     * a certain amount of time has elapsed since the last time a job ran; the last execution time would need to be
     * stored in a database if coordination across separate JVMs is required.
     *
     * @return true if this job should run, false otherwise
     */
    public boolean isActive() {
        return decisionFunction.test(this);
    }

    @Override
    public void handleExceptionSafely(Exception exception) {
        LOG.warn("Encountered exception in job: {}", name);
        lastFailure.set(environment.currentTimeMillis());
        failureCount.incrementAndGet();

        if (nonNull(errorHandler)) {
            errorHandler.handle(this, exception);
        }
    }
}
