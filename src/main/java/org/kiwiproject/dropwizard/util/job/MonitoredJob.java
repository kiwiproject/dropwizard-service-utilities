package org.kiwiproject.dropwizard.util.job;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.concurrent.Async.doAsync;

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

/**
 * Sets up a job from a {@link Runnable} that can be monitored through health checks to ensure it is running correctly.
 */
@Slf4j
@Getter
public class MonitoredJob implements CatchingRunnable {

    private final String name;
    private final Runnable task;
    private final Function<MonitoredJob, Boolean> decisionFunction;
    private final JobErrorHandler errorHandler;
    private final Duration timeout;
    private final KiwiEnvironment environment;

    private final AtomicLong lastSuccess = new AtomicLong();
    private final AtomicLong lastFailure = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
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

    private boolean isActive() {
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
