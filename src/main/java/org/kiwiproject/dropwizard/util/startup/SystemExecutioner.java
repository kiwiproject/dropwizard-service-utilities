package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiDeprecated;

import java.util.concurrent.TimeUnit;

/**
 * Wrapper around {@link System#exit(int)} for situations in which the JVM must be exited. This class is mainly
 * intended for scenarios in which production code may call {@link System#exit(int)} in order to allow unit testing
 * of the exit behavior, but without actually exiting the JVM. For example, a Jetty server that cannot start due to
 * failure to obtain a port, or any other non-recoverable startup error that would otherwise result in a hung or
 * unresponsive server, application, resource, etc. In situations like this, you can test that the
 * {@link SystemExecutioner} would have exited when specific errors occur.
 * <p>
 * The no-args constructor uses the {@link ExecutionStrategies.SystemExitExecutionStrategy}, which uses
 * {@link System#exit(int)} to terminate the JVM. You can supply your own {@link ExecutionStrategy} as well, for
 * example {@link ExecutionStrategies.NoOpExecutionStrategy} is useful in unit tests (so it doesn't actually terminate
 * the JVM).
 *
 * @deprecated This class is moving to kiwi as {@code org.kiwiproject.base.system.SystemExecutioner}
 * in kiwi 5.4.0 and will be removed from this library in 6.0.0. No action is required yet;
 * migration will be possible once kiwi 5.4.0 is available. Note that the kiwi version has a
 * different API: exit methods require an
 * explicit exit code, and the timed exit uses {@link java.time.Duration} instead of
 * {@link java.util.concurrent.TimeUnit}.
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@KiwiDeprecated(
        since = "5.3.0",
        removeAt = "6.0.0",
        replacedBy = "org.kiwiproject.base.system.SystemExecutioner",
        reference = "#673"
)
@SuppressWarnings("removal")
@Slf4j
public class SystemExecutioner {

    /**
     * The execution strategy to use when one of the {@code exit} methods is called.
     */
    @Getter
    private final ExecutionStrategy executionStrategy;

    /**
     * Creates a new {@link SystemExecutioner} using the default {@link ExecutionStrategy}, which is
     * {@link ExecutionStrategies.SystemExitExecutionStrategy}.
     */
    public SystemExecutioner() {
        this(ExecutionStrategies.systemExit());
    }

    /**
     * Creates a new {@link SystemExecutioner} using the given {@link ExecutionStrategy}.
     *
     * @param executionStrategy the strategy to use
     */
    public SystemExecutioner(ExecutionStrategy executionStrategy) {
        this.executionStrategy = requireNotNull(executionStrategy, "executionStrategy must not be null");
    }

    /**
     * Exits immediately.
     */
    public void exit() {
        executionStrategy.exit();
    }

    /**
     * Waits the given amount of time, then exits.
     *
     * @param waitTime     the wait time amount
     * @param waitTimeUnit the wait time unit
     */
    public void exit(long waitTime, TimeUnit waitTimeUnit) {
        LOG.warn("Waiting {} {} before exiting", waitTime, waitTimeUnit);
        new DefaultEnvironment().sleepQuietly(waitTime, waitTimeUnit);
        exit();
    }
}
