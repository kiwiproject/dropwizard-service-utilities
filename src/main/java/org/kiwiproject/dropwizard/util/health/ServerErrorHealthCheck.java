package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.collect.KiwiMaps.isNullOrEmpty;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newResultBuilder;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.metrics.health.HealthStatus;

/**
 * Checks for Status 5xx responses. This uses a meter which is built into Dropwizard Metrics. It is an approximation since it gives
 * a rate instead of a count for a given time duration. This check uses the 15-minute rate on 5xx responses. Thresholds can be
 * set for warning and critical severity. Warning threshold defaults to 1.0 and Critical defaults to 10.0.
 */
@Slf4j
public class ServerErrorHealthCheck extends HealthCheck {

    /**
     * A default name that can be used when registering this health check.
     */
    @SuppressWarnings("unused")
    public static final String DEFAULT_NAME = "5xx Errors";

    @VisibleForTesting
    static final String METER_NAME = "io.dropwizard.jetty.MutableServletContextHandler.5xx-responses";

    @VisibleForTesting
    static final MetricFilter METRIC_FILTER = (name, metric) -> METER_NAME.equals(name);

    @VisibleForTesting
    static final int FIFTEEN_MINUTES_IN_SECONDS = 15 * 60;

    @VisibleForTesting
    static final int DEFAULT_WARNING_THRESHOLD = 1;

    @VisibleForTesting
    static final int DEFAULT_CRITICAL_THRESHOLD = 10;

    private static final String MSG_SUFFIX = "5xx error responses in the last 15 minutes";

    private final MetricRegistry metrics;
    private final int warningThreshold;
    private final int criticalThreshold;

    /**
     * Create the health check with the given {@link MetricRegistry}, defaulting the warning and critical thresholds.
     *
     * @param metrics the Metrics registry
     */
    public ServerErrorHealthCheck(MetricRegistry metrics) {
        this(metrics, DEFAULT_WARNING_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD);
    }

    /**
     * Create the health check with the given {@link MetricRegistry}, warning, and critical thresholds.
     *
     * @param metrics           the Metrics registry
     * @param warningThreshold  the threshold for a warning severity
     * @param criticalThreshold the threshold for a critical severity
     */
    public ServerErrorHealthCheck(MetricRegistry metrics, int warningThreshold, int criticalThreshold) {
        this.metrics = metrics;
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
    }

    @Override
    protected Result check() {
        var meters = metrics.getMeters(METRIC_FILTER);

        if (isNullOrEmpty(meters) || !meters.containsKey(METER_NAME)) {
            return newHealthyResult("%s meter is not configured", METER_NAME);
        }

        var meter = meters.get(METER_NAME);
        var estimatedErrorCount = meter.getFifteenMinuteRate() * FIFTEEN_MINUTES_IN_SECONDS;

        LOG.trace("15 minute rate on 5xx responses meter is roughly {}", estimatedErrorCount);

        return setupResultBuilder(estimatedErrorCount)
                .withDetail("rate", meter.getFifteenMinuteRate())
                .withDetail("approximateCount", toDoubleWithOneDecimalPlace(estimatedErrorCount))
                .withDetail("warningThreshold", warningThreshold)
                .withDetail("criticalThreshold", criticalThreshold)
                .withDetail("meter", METER_NAME)
                .build();
    }

    private ResultBuilder setupResultBuilder(double estimatedErrorCount) {
        if (estimatedErrorCount >= criticalThreshold) {
            return newResultBuilder(false, HealthStatus.CRITICAL)
                    .withMessage("Critical level of %s", MSG_SUFFIX);
        } else if (estimatedErrorCount >= warningThreshold) {
            return newResultBuilder(false, HealthStatus.WARN)
                    .withMessage("Some %s", MSG_SUFFIX);
        }

        return newResultBuilder(true)
                .withMessage("No %s", MSG_SUFFIX);
    }

    // I am 100% sure this isn't even remotely close to the most efficient way to do this.
    // Feel free to improve it and submit a PR.
    @VisibleForTesting
    static double toDoubleWithOneDecimalPlace(double value) {
        return Double.parseDouble(String.format("%.1f", value));
    }
}
