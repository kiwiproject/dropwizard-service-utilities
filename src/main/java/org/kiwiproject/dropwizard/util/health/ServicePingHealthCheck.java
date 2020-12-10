package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.collect.KiwiArrays.isNullOrEmpty;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.kiwiproject.jaxrs.KiwiResponses;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.ServiceIdentifier;
import org.kiwiproject.jersey.client.exception.MissingServiceRuntimeException;
import org.kiwiproject.metrics.health.HealthStatus;
import org.kiwiproject.registry.model.Port.PortType;

import javax.ws.rs.client.WebTarget;
import java.util.stream.Stream;

/**
 * Health check to perform a ping on a configured dependent service to ensure the service is at least running.
 */
@Slf4j
public class ServicePingHealthCheck extends HealthCheck {

    /**
     * Indicator of how important a dependent service is to the current service.
     * <ul>
     *     <li>REQUIRED indicates that the current service cannot function without the dependency.</li>
     *     <li>
     *         WANTED indicates that the current service wants the dependency to function, but can operate in degraded
     *         mode without it.
     *     </li>
     *     <li>
     *         INFORMATIONAL indicates that the current service uses the dependency for informational purposes and
     *         not for critical logic.
     *     </li>
     * </ul>
     */
    public enum DependencyImportance {
        INFORMATIONAL(HealthStatus.INFO),
        WANTED(HealthStatus.WARN),
        REQUIRED(HealthStatus.CRITICAL);

        private final HealthStatus healthStatus;

        DependencyImportance(HealthStatus healthStatus) {
            this.healthStatus = healthStatus;
        }
    }

    private final ServiceIdentifier serviceIdentifier;
    private final RegistryAwareClient client;

    @VisibleForTesting
    final DependencyImportance dependencyImportance;

    /**
     * Creates the health check set up to ping a service defined by the given {@link ServiceIdentifier} using the given
     * {@link RegistryAwareClient}. By default the service to be checked will be marked as REQUIRED.
     *
     * @param serviceIdentifier the identifier for the service to be pinged.
     * @param client            the {@link RegistryAwareClient} to use for service lookup and to perform the request
     */
    public ServicePingHealthCheck(ServiceIdentifier serviceIdentifier, RegistryAwareClient client) {
        this(serviceIdentifier, client, DependencyImportance.REQUIRED);
    }

    /**
     * Creates the health check set up to ping a service defined by the given {@link ServiceIdentifier} using the given
     * {@link RegistryAwareClient} and marking the importance of the dependency by the given
     * {@link DependencyImportance}.
     *
     * @param serviceIdentifier     the identifier for the service to be pinged.
     * @param client                the {@link RegistryAwareClient} to use for service lookup and to perform the request
     * @param dependencyImportance  the importance of the dependent service to the current service
     */
    public ServicePingHealthCheck(ServiceIdentifier serviceIdentifier,
                                  RegistryAwareClient client,
                                  DependencyImportance dependencyImportance) {
        this.serviceIdentifier = requireNotNull(serviceIdentifier, "serviceIdentifier is required");
        this.client = requireNotNull(client, "client is required");
        this.dependencyImportance = requireNotNull(dependencyImportance, "dependencyPriority is required");
    }

    @Override
    protected Result check() throws Exception {
        WebTarget target;

        try {
            target = getStatusPathTarget();
        } catch (MissingServiceRuntimeException e) {
            return unhealthyResult(format("Service {} not found", serviceIdentifier.getServiceName()), e);
        }

        try {
            var response = target
                    .request()
                    .get();

            return KiwiResponses.onSuccessOrFailureWithResult(response,
                    successResponse -> newHealthyResult(),
                    failResponse -> unhealthyResult(
                            notOkStatusMessage(target.getUri().toString(), failResponse.getStatus())));

        } catch (Exception e) {
            var msg = format("Exception pinging service {} at {}: {} ({})",
                    serviceIdentifier.getServiceName(), target.getUri().toString(), e.getMessage(),
                    e.getClass().getName());

            return unhealthyResult(msg, e);
        }
    }

    private WebTarget getStatusPathTarget() {
        return client.targetForService(serviceIdentifier, PortType.ADMIN,
                instance -> instance.getPaths().getStatusPath());
    }

    private String notOkStatusMessage(String uri, int status) {
        return format("Ping to service {} at {} returned non-OK status {}",
                serviceIdentifier.getServiceName(), uri, status);
    }

    private Result unhealthyResult(String message, Exception e) {
        LOG.warn(message, e);
        return newUnhealthyResult(dependencyImportance.healthStatus, message);
    }

    private Result unhealthyResult(String message) {
        LOG.warn(message);
        return newUnhealthyResult(dependencyImportance.healthStatus, message);
    }

    /**
     * Registers a {@link ServicePingHealthCheck} for each of the given {@link ServiceIdentifier}s given, defaulting the
     * importance to REQUIRED.
     *
     * @param healthCheckRegistry   the {@link HealthCheckRegistry} to register the health checks.
     * @param client                the {@link RegistryAwareClient} to use for service lookup and to perform the request.
     * @param serviceIdentifiers    the identifiers for the service to be pinged.
     */
    public static void registerServiceChecks(HealthCheckRegistry healthCheckRegistry,
                                             RegistryAwareClient client,
                                             ServiceIdentifier... serviceIdentifiers) {

        registerServiceChecks(healthCheckRegistry, client, DependencyImportance.REQUIRED, serviceIdentifiers);
    }

    /**
     * Registers a {@link ServicePingHealthCheck} for each of the given {@link ServiceIdentifier}s given, using the
     * given importance <em>for each</em>.
     * <p>
     * NOTE: If different importances are required for different services, then call this method for each importance.
     *
     * @param healthCheckRegistry   the {@link HealthCheckRegistry} to register the health checks.
     * @param client                the {@link RegistryAwareClient} to use for service lookup and to perform the request.
     * @param importance            the {@link DependencyImportance} of the services being checked.
     * @param serviceIdentifiers    the identifiers for the service to be pinged.
     */
    public static void registerServiceChecks(HealthCheckRegistry healthCheckRegistry,
                                             RegistryAwareClient client,
                                             DependencyImportance importance,
                                             ServiceIdentifier... serviceIdentifiers) {

        checkArgumentNotNull(healthCheckRegistry, "healthCheckRegistry is required");
        checkArgumentNotNull(client, "client is required");

        if (isNullOrEmpty(serviceIdentifiers)) {
            LOG.warn("No services were supplied, so no ServicePingHealthChecks will be added");
            return;
        }

        Stream.of(serviceIdentifiers).forEach(identifier -> {
            var healthCheckName = nameFor(identifier);
            var healthCheck = new ServicePingHealthCheck(identifier, client, importance);
            healthCheckRegistry.register(healthCheckName, healthCheck);
        });
    }

    /**
     * Resolves a health check name for a given {@link ServiceIdentifier}.
     *
     * @param identifier The {@link ServiceIdentifier} with a name being resolved.
     * @return a health check name to match the given {@link ServiceIdentifier}.
     */
    public static String nameFor(ServiceIdentifier identifier) {
        var humanizedName = WordUtils.capitalizeFully(identifier.getServiceName(), '-')
                .replace('-', ' ');

        return format("Ping: {}", humanizedName);
    }
}
