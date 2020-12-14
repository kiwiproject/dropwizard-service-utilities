package org.kiwiproject.dropwizard.util.health;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.util.Duration;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.job.MonitoredJob;

import java.time.Instant;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.metrics.health.HealthCheckResults.*;

/**
 * Health check that monitors a {@link MonitoredJob} to ensure that it is running on schedule and not encountering
 * errors while running.
 * <p>
 * <b>Builder Parameters:</b>
 * <table>
 *     <tr>
 *         <td>Name</td>
 *         <td>Default</td>
 *         <td>Description</td>
 *     </tr>
 *     <tr>
 *         <td>{@code job}</td>
 *         <td>None - throws {@link IllegalArgumentException} if missing</td>
 *         <td>A {@link MonitoredJob} to watch</td>
 *     </tr>
 *     <tr>
 *         <td>{@code expectedFrequency}</td>
 *         <td>None - throws {@link IllegalArgumentException} if missing</td>
 *         <td>
 *             A {@link Duration} that describes the expected amount of time between each job run. Used to determine if
 *             the job is running slow or has stopped running.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code errorWarningDuration}</td>
 *         <td>15 minutes</td>
 *         <td>
 *             A {@link Duration} that is used to mark the health check unhealthy if an error occurs within this
 *             duration's timeframe in the past from now.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code thresholdFactor}</td>
 *         <td>2.0</td>
 *         <td>The factor to apply to the {@code expectedFrequency} so that the health check does not over report.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code lowerTimeBound}</td>
 *         <td>Current time in milliseconds when the health check is created</td>
 *         <td>
 *             Provides a time to check against in the event that the job has not run when the first
 *             health check is checked.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code environment}</td>
 *         <td>A {@link DefaultEnvironment}</td>
 *         <td>The {@link KiwiEnvironment} to use for looking up the current time.</td>
 *     </tr>
 * </table>
 */
@Slf4j
@Getter(AccessLevel.PACKAGE) // For testing purposes
public class MonitoredJobHealthCheck extends HealthCheck {

    @VisibleForTesting
    static final double DEFAULT_THRESHOLD_FACTOR = 2.0;

    @VisibleForTesting
    static final Duration MINIMUM_WARNING_THRESHOLD = Duration.minutes(1);

    @VisibleForTesting
    static final Duration DEFAULT_WARNING_DURATION = Duration.minutes(15);

    private final MonitoredJob job;
    private final Duration expectedFrequency;
    private final Duration errorWarningDuration;
    private final double thresholdFactor;
    private final long lowerTimeBound;
    private final KiwiEnvironment kiwiEnvironment;

    @Builder
    private MonitoredJobHealthCheck(MonitoredJob job,
                                    Duration expectedFrequency,
                                    Duration errorWarningDuration,
                                    Double thresholdFactor,
                                    Long lowerTimeBound,
                                    KiwiEnvironment environment) {

        this.job = requireNotNull(job, "job is required");
        this.expectedFrequency = requireNotNull(expectedFrequency, "expectedFrequency is required");
        this.errorWarningDuration = isNull(errorWarningDuration) ? DEFAULT_WARNING_DURATION : errorWarningDuration;
        this.thresholdFactor = isNull(thresholdFactor) ? DEFAULT_THRESHOLD_FACTOR : thresholdFactor;
        this.kiwiEnvironment = isNull(environment) ? new DefaultEnvironment() : environment;
        this.lowerTimeBound = isNull(lowerTimeBound) ? kiwiEnvironment.currentTimeMillis() : lowerTimeBound;
    }

    @Override
    protected Result check() throws Exception {
        try {
            var lastRun = job.getLastSuccess().get();
            if (!job.isActive()) {
                return buildHealthyResult(f("Job is inactive. (last run: {})", instantToStringOrNever(lastRun)));
            }

            var now = kiwiEnvironment.currentTimeMillis();
            var lastFailure = job.getLastFailure().get();
            if ((now - lastFailure) < errorWarningDuration.toMilliseconds()) {
                return buildUnhealthyResult(f("An error has occurred at: {}, which is within the threshold of: {}",
                        instantToStringOrNever(job.getLastFailure().get()), errorWarningDuration));
            }

            var warningThreshold = getWarningThreshold();
            if ((now - getTimeOrServerStart(lastRun)) > warningThreshold) {
                return buildUnhealthyResult(f("Last successful execution was: {}, which is older than the threshold of: {} seconds",
                        instantToStringOrNever(lastRun), Duration.milliseconds(warningThreshold).toSeconds()));
            }

            return buildHealthyResult(f("Last successful execution was: {}", instantToStringOrNever(lastRun)));
        } catch (Exception e) {
            LOG.error("Encountered Exception: ", e);
            return handleException(e);
        }
    }

    private Result buildHealthyResult(String message) {
        return resultBuilderWith(message, true).build();
    }

    private ResultBuilder resultBuilderWith(String message, boolean healthy) {
        return resultBuilderWith(message, healthy, null);
    }

    private ResultBuilder resultBuilderWith(String message, boolean healthy, Exception error) {
        checkValidHealthArgumentCombination(healthy, error);
        var resultBuilder = isNull(error) ? newResultBuilder(healthy)
                : newUnhealthyResultBuilder(error);

        return resultBuilder
                .withMessage(message)
                .withDetail("jobName", job.getName())
                .withDetail("totalErrors", job.getFailureCount())
                .withDetail("lastFailure", job.getLastFailure())
                .withDetail("lastSuccess", job.getLastSuccess())
                .withDetail("lastExecutionTimeMs", job.getLastExecutionTime())
                .withDetail("expectedFrequencyMs", expectedFrequency.toMilliseconds())
                .withDetail("warningThresholdMs", getWarningThreshold())
                .withDetail("errorWarningDurationMs", errorWarningDuration.toMilliseconds());
    }

    private static void checkValidHealthArgumentCombination(boolean healthy, Exception error) {
        checkArgument(!healthy || isNull(error), "If healthy, error must be null!");
    }

    private long getWarningThreshold() {
        return Math.max((long) (expectedFrequency.toMilliseconds() * thresholdFactor),
                MINIMUM_WARNING_THRESHOLD.toMilliseconds());
    }

    private static String instantToStringOrNever(long epochMs) {
        return epochMs != 0 ? Instant.ofEpochMilli(epochMs).toString() : "never";
    }

    private Result buildUnhealthyResult(String message) {
        return resultBuilderWith(message, false).build();
    }

    private long getTimeOrServerStart(long lastRun) {
        return Math.max(lastRun, lowerTimeBound);
    }

    private Result handleException(Exception e) {
        try {
            LOG.trace("Handling {} exception with message: {}", e.getClass().getName(), e.getMessage());
            return resultBuilderWith("Encountered failure performing health check", false, e).build();
        } catch (Exception unexpectedException) {
            LOG.error("Encountered exception creating error result: ", unexpectedException);
            return newUnhealthyResult(unexpectedException);
        }
    }
}
