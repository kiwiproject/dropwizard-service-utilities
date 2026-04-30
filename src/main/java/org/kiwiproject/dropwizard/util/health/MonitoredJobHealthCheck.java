package org.kiwiproject.dropwizard.util.health;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.metrics.health.HealthCheckResults.newResultBuilder;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResultBuilder;
import static org.kiwiproject.time.KiwiDurationFormatters.formatDropwizardDurationWords;
import static org.kiwiproject.time.KiwiDurationFormatters.formatMillisecondDurationWords;

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
import java.util.function.Supplier;

/**
 * Health check that monitors a {@link MonitoredJob} to ensure that it is running on schedule and not encountering
 * errors while running.
 * <p>
 * <b>Builder Parameters:</b>
 * <table>
 *     <caption>Monitored Job Health Check Builder Params</caption>
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
 *     <tr>
 *         <td>{@code suppressWarningThreshold}</td>
 *         <td>A supplier that always returns {@code false} (never suppress)</td>
 *         <td>
 *             An optional {@link Supplier} that returns {@code true} when the warning threshold should be
 *             suppressed, causing the health check to report healthy even if the time since the last successful
 *             execution exceeds the warning threshold. Useful when a job is expected to run longer than usual
 *             due to known conditions, such as a large deployment. If not provided, the warning threshold is
 *             never suppressed.
 *         </td>
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
    private final long expectedFrequencyMilliseconds;
    private final String expectedFrequencyString;
    private final Duration errorWarningDuration;
    private final String errorWarningDurationString;
    private final long errorWarningDurationMilliseconds;
    private final double thresholdFactor;
    private final long lowerTimeBoundTimestampMillis;
    private final KiwiEnvironment kiwiEnvironment;
    private final long warningThresholdDurationMilliseconds;
    private final String warningThresholdDurationString;
    private final Supplier<Boolean> suppressWarningThreshold;

    @Builder
    private MonitoredJobHealthCheck(MonitoredJob job,
                                    Duration expectedFrequency,
                                    Duration errorWarningDuration,
                                    Double thresholdFactor,
                                    Long lowerTimeBound,
                                    KiwiEnvironment environment,
                                    Supplier<Boolean> suppressWarningThreshold) {

        this.job = requireNotNull(job, "job is required");
        this.expectedFrequencyMilliseconds = requireNotNull(expectedFrequency, "expectedFrequency is required").toMilliseconds();
        this.expectedFrequencyString = formatMillisecondDurationWords(this.expectedFrequencyMilliseconds);
        this.errorWarningDuration = isNull(errorWarningDuration) ? DEFAULT_WARNING_DURATION : errorWarningDuration;
        this.errorWarningDurationString = formatDropwizardDurationWords(this.errorWarningDuration);
        this.errorWarningDurationMilliseconds = this.errorWarningDuration.toMilliseconds();
        this.thresholdFactor = isNull(thresholdFactor) ? DEFAULT_THRESHOLD_FACTOR : thresholdFactor;
        this.kiwiEnvironment = isNull(environment) ? new DefaultEnvironment() : environment;
        this.lowerTimeBoundTimestampMillis = isNull(lowerTimeBound) ? kiwiEnvironment.currentTimeMillis() : lowerTimeBound;
        this.warningThresholdDurationMilliseconds = calculateWarningThreshold(expectedFrequencyMilliseconds, this.thresholdFactor);
        this.warningThresholdDurationString = formatMillisecondDurationWords(this.warningThresholdDurationMilliseconds);
        this.suppressWarningThreshold = isNull(suppressWarningThreshold)
                ? () -> false
                : suppressWarningThreshold;
    }

    @Override
    protected Result check() {
        try {
            var lastSuccess = job.lastSuccessMillis();
            if (!job.isActive()) {
                var message = f("Job is inactive. (last run: {})", instantToStringOrNever(lastSuccess));
                return buildHealthyResult(message);
            }

            var now = kiwiEnvironment.currentTimeMillis();
            var lastFailure = job.lastFailureMillis();
            var timeSinceLastFailure = now - lastFailure;
            if (timeSinceLastFailure < errorWarningDurationMilliseconds) {
                return buildUnhealthyResult(f("An error has occurred at: {}, which is within the threshold of: {}",
                        instantToStringOrNever(lastFailure), errorWarningDurationString));
            }

            var timeSinceLastSuccess = now - getTimeOrServerStart(lastSuccess);
            if (timeSinceLastSuccess > warningThresholdDurationMilliseconds) {
                var warningThresholdSuppressed = Boolean.TRUE.equals(suppressWarningThreshold.get());
                if (warningThresholdSuppressed) {
                    LOG.debug("Job [{}] has exceeded warning threshold of {} but suppression is active",
                            job.getName(), warningThresholdDurationString);
                    var message = f("Last successful execution was: {} (warning threshold of {} is suppressed)",
                            instantToStringOrNever(lastSuccess), warningThresholdDurationString);
                    return buildHealthyResult(message, true);
                }

                return buildUnhealthyResult(f("Last successful execution was: {}, which is older than the threshold of: {}",
                        instantToStringOrNever(lastSuccess), warningThresholdDurationString));
            }

            var message = f("Last successful execution was: {}", instantToStringOrNever(lastSuccess));
            return buildHealthyResult(message);
        } catch (Exception e) {
            LOG.error("Encountered Exception: ", e);
            return handleException(e);
        }
    }

    private Result buildHealthyResult(String message) {
        return buildHealthyResult(message, false);
    }

    private Result buildHealthyResult(String message, boolean warningThresholdSuppressed) {
        return resultBuilderWith(message, true, warningThresholdSuppressed).build();
    }

    private ResultBuilder resultBuilderWith(String message,
                                            boolean healthy,
                                            boolean warningThresholdSuppressed) {
        return resultBuilderWith(message, healthy, warningThresholdSuppressed, null);
    }

    private ResultBuilder resultBuilderWith(String message,
                                            boolean healthy,
                                            boolean warningThresholdSuppressed,
                                            Exception error) {
        checkValidHealthArgumentCombination(healthy, error);
        var resultBuilder = isNull(error) ? newResultBuilder(healthy)
                : newUnhealthyResultBuilder(error);

        var lastFailureMillis = job.lastFailureMillis();
        var lastSuccessMillis = job.lastSuccessMillis();
        var lastExecutionTimeMillis = job.lastExecutionTimeMillis();

        return resultBuilder
                .withMessage(message)
                .withDetail("jobName", job.getName())
                .withDetail("totalErrors", job.failureCount())
                .withDetail("lastFailureTimestamp", lastFailureMillis)
                .withDetail("lastFailureTime", instantToStringOrNever(lastFailureMillis))
                .withDetail("lastJobExceptionInfo", job.lastJobExceptionInfo())
                .withDetail("lastSuccessTimestamp", lastSuccessMillis)
                .withDetail("lastSuccessTime", instantToStringOrNever(lastSuccessMillis))
                .withDetail("lastSuccessfulExecutionDurationMs", lastExecutionTimeMillis)
                .withDetail("lastSuccessfulExecutionDuration", formatMillisecondDurationWords(lastExecutionTimeMillis))
                .withDetail("expectedJobFrequencyMs", expectedFrequencyMilliseconds)
                .withDetail("expectedJobFrequency", expectedFrequencyString)
                .withDetail("warningThresholdDurationMs", warningThresholdDurationMilliseconds)
                .withDetail("warningThresholdDuration", warningThresholdDurationString)
                .withDetail("recentErrorWarningDurationMs", errorWarningDurationMilliseconds)
                .withDetail("recentErrorWarningDuration", errorWarningDurationString)
                .withDetail("warningThresholdSuppressed", warningThresholdSuppressed);
    }

    private static void checkValidHealthArgumentCombination(boolean healthy, Exception error) {
        checkArgument(!healthy || isNull(error), "If healthy, error must be null!");
    }

    private static long calculateWarningThreshold(long expectedFrequencyMilliseconds, double thresholdFactor) {
        return (long) Math.max(
                expectedFrequencyMilliseconds * thresholdFactor,
                MINIMUM_WARNING_THRESHOLD.toMilliseconds()
        );
    }

    private static String instantToStringOrNever(long epochMillis) {
        return epochMillis != 0 ? Instant.ofEpochMilli(epochMillis).toString() : "never";
    }

    private Result buildUnhealthyResult(String message) {
        return resultBuilderWith(message, false, false).build();
    }

    private long getTimeOrServerStart(long lastSuccessMillis) {
        return Math.max(lastSuccessMillis, lowerTimeBoundTimestampMillis);
    }

    private Result handleException(Exception e) {
        try {
            LOG.trace("Handling {} exception with message: {}", e.getClass().getName(), e.getMessage());
            return resultBuilderWith("Encountered failure performing health check", false, false, e).build();
        } catch (Exception unexpectedException) {
            LOG.error("Encountered exception creating error result: ", unexpectedException);
            return newUnhealthyResult(unexpectedException);
        }
    }
}
