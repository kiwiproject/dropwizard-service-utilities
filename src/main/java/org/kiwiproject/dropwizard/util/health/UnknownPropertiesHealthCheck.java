package org.kiwiproject.dropwizard.util.health;

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

    private final LoggingDeserializationProblemHandler handler;

    public UnknownPropertiesHealthCheck(LoggingDeserializationProblemHandler handler) {
        this.handler = requireNotNull(handler, "handler is required");
    }

    @Override
    protected Result check() throws Exception {
        if (handler.getUnknownPropertyCount() == 0) {
            return newHealthyResult("No unknown properties detected");
        }

        return newUnhealthyResultBuilder(HealthStatus.INFO)
                .withMessage("%d unknown properties detected", handler.getUnknownPropertyCount())
                .withDetail("unexpectedPaths", handler.getUnexpectedPaths())
                .build();
    }
}
