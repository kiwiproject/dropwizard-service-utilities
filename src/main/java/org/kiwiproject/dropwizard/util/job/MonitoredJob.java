package org.kiwiproject.dropwizard.util.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.kiwiproject.base.CatchingRunnable;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.concurrent.Async.doAsync;

/**
 * Sets up a job from a {@link Runnable} that can be monitored through health checks to ensure it is running correctly.
 */
@Slf4j
public class MonitoredJob implements CatchingRunnable {

    private final Runnable task;
    private final JobErrorHandler errorHandler;
    private final Duration timeout;

    @Getter
    private final String name;

    @Getter(AccessLevel.PACKAGE)
    private final Function<MonitoredJob, Boolean> decisionFunction;

    @Getter(AccessLevel.PACKAGE)
    private final KiwiEnvironment environment;

    @Getter
    private final AtomicLong lastSuccess = new AtomicLong();

    @Getter
    private final AtomicLong lastFailure = new AtomicLong();

    @Getter
    private final AtomicLong failureCount = new AtomicLong();

    @Getter
    private final AtomicLong lastExecutionTime = new AtomicLong();

    @Builder
    private MonitoredJob(String name,
                         Runnable task,
                         Function<MonitoredJob, Boolean> decisionFunction,
                         JobErrorHandler errorHandler,
                         Duration timeout,
                         KiwiEnvironment environment) {
        this.name = requireNotBlank(name, "name is required");
        this.task = requireNotNull(task, "task is required");
        this.decisionFunction = isNull(decisionFunction) ? (job -> true) : decisionFunction;
        this.errorHandler = errorHandler;
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
     * Checks if the job should be active and execute. This is useful if the same job runs in separate JVMs but only a
     * single one of the jobs should run at a time.
     *
     * @return true if this job should run, false otherwise
     */
    public boolean isActive() {
        var result = decisionFunction.apply(this);
        return BooleanUtils.isTrue(result);
    }

    @Override
    public void handleExceptionSafely(Throwable throwable) {
        LOG.warn("Encountered exception in job: {}", name);
        lastFailure.set(environment.currentTimeMillis());
        failureCount.incrementAndGet();

        if (nonNull(errorHandler)) {
            errorHandler.handle(this, throwable);
        }
    }
}
