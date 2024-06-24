package org.kiwiproject.dropwizard.util.health;

import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.jaxrs.KiwiResponses.successfulAlwaysClosing;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResultBuilder;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.client.Client;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.metrics.health.HealthStatus;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Simple health check to ensure that a URL can be reached and that it returns
 * a 2xx response.
 */
@Slf4j
public class UrlHealthCheck extends HealthCheck {

    @VisibleForTesting
    static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm 'UTC on' yyyy-MM-dd");

    private final Client client;
    private final String description;
    private final String url;
    private final Supplier<Boolean> executionCondition;
    private final KiwiEnvironment kiwiEnvironment;

    /**
     * Create a new instance which always executes when health checks are called.
     *
     * @param client the HTTP client to use when connecting to the URL
     * @param description a human-friendly description of the URL (e.g., "prod web 1")
     * @param url the URL to check
     */
    public UrlHealthCheck(Client client, String description, String url) {
        this(client, description, url, () -> true);
    }

    /**
     * Create a new instance which executes when the {@code executionCondition} returns
     * {@code true}.
     * <p>
     * This is useful when there are multiple instances of a service, and you only want
     * one of them to execute the health check, for example, the "leader" when using a
     * "Leader Latch" pattern.
     *
     * @param client the HTTP client to use when connecting to the URL
     * @param description a human-friendly description of the URL (e.g., "prod web 1")
     * @param url the URL to check
     * @param executionCondition whether to execute the health check when called
     */
    public UrlHealthCheck(Client client,
                          String description,
                          String url,
                          Supplier<Boolean> executionCondition) {
        this(client, description, url, executionCondition, new DefaultEnvironment());
    }

    @VisibleForTesting
    UrlHealthCheck(Client client,
                   String description,
                   String url,
                   Supplier<Boolean> executionCondition,
                   KiwiEnvironment kiwiEnvironment) {

        this.client = requireNotNull(client, "client must not bu null");
        this.description = requireNotBlank(description, "description must not be blank");
        this.url = requireNotBlank(url, "url must not be blank");
        this.executionCondition = requireNotNull(executionCondition, "executionCondition must not be null");
        this.kiwiEnvironment = requireNotNull(kiwiEnvironment, "kiwiEnvironment must not be null");
    }

    /**
     * Checks that the URL returns a 2xx response when the execution condition is true.
     * <p>
     * If the execution condition is false, skips the check and returns a "healthy"
     * result. If the execution condition throws an Exception, assumes that the
     * check should be performed and logs an error.
     */
    @Override
    protected Result check() {
        if (shouldNotExecute()) {
            LOG.trace("executionCondition evaluated to false. Skip check and return 'healthy'");
            return newHealthyResult("executionCondition evaluated to false; check skipped and reported as healthy");
        }

        return checkHealth();
    }

    private boolean shouldNotExecute() {
        return !shouldExecute();
    }

    private boolean shouldExecute() {
        try {
            var result = executionCondition.get();
            return nonNull(result) && result;
        } catch (Exception e) {
            LOG.error("executionCondition threw an exception. Assuming the health check should execute.", e);
            return true;
        }
    }

    private Result checkHealth() {
        var readableLastCheckTime = readableInstant(kiwiEnvironment.currentInstant());

        try {
            var response = client.target(url).request().get();
            var status = response.getStatus();
            if (successfulAlwaysClosing(response)) {
                return newHealthyResult(
                    "Got successful %d response from %s at %s (checked at: %s)",
                    status, description, url, readableLastCheckTime);
            } else {
                return newUnhealthyResultBuilder(HealthStatus.WARN)
                        .withMessage("Got unsuccessful %d response from %s at %s." +
                                " It may not be functioning properly. (checked at: %s)",
                                status, description, url, readableLastCheckTime)
                        .build();
            }
        } catch (Exception e) {
            return newUnhealthyResultBuilder(HealthStatus.CRITICAL, e)
                    .withMessage("Got %s making call to %s at %s." +
                            " It may be down or unreachable! (checked at: %s)",
                            e.getClass().getName(), description, url, readableLastCheckTime)
                    .build();
        }
    }

    @VisibleForTesting
    static String readableInstant(Instant instant) {
        return FORMAT.format(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
}
