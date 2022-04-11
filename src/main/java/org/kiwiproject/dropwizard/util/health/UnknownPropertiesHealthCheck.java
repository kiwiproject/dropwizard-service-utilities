package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiPreconditions.checkPositive;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResultBuilder;

import com.codahale.metrics.health.HealthCheck;
import org.kiwiproject.json.LoggingDeserializationProblemHandler;
import org.kiwiproject.metrics.health.HealthStatus;

/**
 * Health check that checks for unknown properties found during Jackson deserialization.
 *
 * @see LoggingDeserializationProblemHandler
 */
public class UnknownPropertiesHealthCheck extends HealthCheck {

    /**
     * A default name that can be used when registering this health check.
     */
    @SuppressWarnings("unused")
    public static final String DEFAULT_NAME = "Unknown JSON Properties";

    private final LoggingDeserializationProblemHandler handler;

    public UnknownPropertiesHealthCheck(LoggingDeserializationProblemHandler handler) {
        this.handler = requireNotNull(handler, "handler is required");
    }

    @Override
    protected Result check() {
        if (handler.getUnknownPropertyCount() == 0) {
            return newHealthyResult("No unknown properties detected");
        }

        return newUnhealthyResultBuilder(HealthStatus.INFO)
                .withMessage(unknownPropertiesMessage(handler.getUnknownPropertyCount()))
                .withDetail("unexpectedPaths", handler.getUnexpectedPropertyPaths())
                .build();
    }

    private String unknownPropertiesMessage(long count) {
        checkPositive(count, "expected 1 or higher");
        return count == 1 ? "1 unknown property detected" : (count + " unknown properties detected");
    }
}
