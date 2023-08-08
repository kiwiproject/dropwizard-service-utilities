package org.kiwiproject.dropwizard.util.health;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newResultBuilder;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Health check that checks the percent of leased connections against the maximum number of connections for
 * JAX-RS {@link jakarta.ws.rs.client.Client} instances that were created using Dropwizard's
 * {@code io.dropwizard.client.JerseyClientBuilder}, which creates an HTTP connection pool and registers various
 * connection metrics that we can query.
 * <p>
 * Please note, if using a default JAX-RS {@link jakarta.ws.rs.client.Client} created using the normal
 * {@link jakarta.ws.rs.client.ClientBuilder}, those clients will <em>not</em> have metrics registered and will therefore
 * <em>not</em> be included by this check (since it won't know about them).
 */
@Slf4j
public class HttpConnectionsHealthCheck extends HealthCheck {

    /**
     * A default name that can be used when registering this health check.
     */
    @SuppressWarnings("unused")
    public static final String DEFAULT_NAME = "HTTP Connections";

    /**
     * Default percent above which this health check will report unhealthy.
     */
    public static final double DEFAULT_WARNING_THRESHOLD = 50.0;

    private static final String HTTP_CONN_MANAGER_GAUGE_PREFIX = "org.apache.http.conn.HttpClientConnectionManager.";
    private static final int START_INDEX = HTTP_CONN_MANAGER_GAUGE_PREFIX.length();

    private final MetricRegistry metrics;
    private final double warningThreshold;

    public HttpConnectionsHealthCheck(MetricRegistry metrics) {
        this(metrics, DEFAULT_WARNING_THRESHOLD);
    }

    public HttpConnectionsHealthCheck(MetricRegistry metrics, double warningThreshold) {
        this.metrics = requireNotNull(metrics);

        checkArgument(warningThreshold > 0.0 && warningThreshold < 100.0, IllegalArgumentException.class,
                "warningThreshold must be more than 0 and less than 100");

        this.warningThreshold = warningThreshold;
    }

    @Override
    protected Result check() {
        var httpMetrics = metrics.getGauges((name, metric) -> name.startsWith(HTTP_CONN_MANAGER_GAUGE_PREFIX));
        var clientNames = findHttpClientMetricNames(httpMetrics);

        LOG.trace("Client names: {}", clientNames);

        if (clientNames.isEmpty()) {
            return newHealthyResult("No HTTP clients found with metrics");
        }

        var clientHealth = getClientHealth(httpMetrics, clientNames);
        return determineResult(clientHealth, clientNames.size());
    }

    @SuppressWarnings("rawtypes")
    private static Set<String> findHttpClientMetricNames(SortedMap<String, Gauge> httpMetrics) {
        return httpMetrics.keySet().stream()
                .map(HttpConnectionsHealthCheck::clientNameFrom)
                .collect(toSet());
    }

    private static String clientNameFrom(String gaugeName) {
        return gaugeName.substring(START_INDEX, gaugeName.lastIndexOf('.'));
    }

    @SuppressWarnings("rawtypes")
    private Map<ClientConnectionInfo.HealthStatus, List<ClientConnectionInfo>> getClientHealth(
            SortedMap<String, Gauge> httpMetrics, Set<String> clientNames) {

        return clientNames.stream()
                .map(clientName -> getClientConnectionInfo(httpMetrics, clientName))
                .collect(groupingBy(ClientConnectionInfo::getHealthStatus));
    }

    @SuppressWarnings("rawtypes")
    private ClientConnectionInfo getClientConnectionInfo(SortedMap<String, Gauge> httpMetrics, String clientName) {
        var leasedConnectionsGauge = httpMetrics.get(leasedConnectionsGaugeName(clientName));
        var maxConnectionsGauge = httpMetrics.get(maxConnectionsGaugeName(clientName));

        var leasedConnections = (int) leasedConnectionsGauge.getValue();
        var maxConnections = (int) maxConnectionsGauge.getValue();

        var connectionInfo = ClientConnectionInfo.builder()
                .clientName(clientName)
                .leased(leasedConnections)
                .max(maxConnections)
                .warningThreshold(warningThreshold)
                .build();

        LOG.trace("{}: {} of {} leased ({}%)", clientName, leasedConnections, maxConnections,
                connectionInfo.percentLeased);

        return connectionInfo;
    }

    private Result determineResult(Map<ClientConnectionInfo.HealthStatus, List<ClientConnectionInfo>> clientHealth,
                                   int totalNumberOfClients) {

        var healthyClients = getConnectionInfoMap(clientHealth, ClientConnectionInfo.HealthStatus.HEALTHY);
        var unhealthyClients = getConnectionInfoMap(clientHealth, ClientConnectionInfo.HealthStatus.UNHEALTHY);

        var isHealthy = unhealthyClients.isEmpty();

        var builder = newResultBuilder(isHealthy)
                .withDetail("healthyClients", healthyClients)
                .withDetail("unhealthyClients", unhealthyClients);

        if (isHealthy) {
            return builder
                    .withMessage("%d HTTP client(s) < %4.1f%% leased connections.",
                            totalNumberOfClients,
                            warningThreshold)
                    .build();
        }

        LOG.trace("Unhealthy clients: {}", unhealthyClients);

        return builder
                .withMessage("%d of %d HTTP client(s) >= %4.1f%% leased connections.",
                        unhealthyClients.size(),
                        totalNumberOfClients,
                        warningThreshold)
                .build();
    }

    private Map<String, ClientConnectionInfo> getConnectionInfoMap(
            Map<ClientConnectionInfo.HealthStatus, List<ClientConnectionInfo>> clientHealth,
            ClientConnectionInfo.HealthStatus healthStatus) {

        return clientHealth.getOrDefault(healthStatus, emptyList()).stream()
                .collect(toMap(ClientConnectionInfo::getClientName, identity()));
    }

    private static String leasedConnectionsGaugeName(String clientName) {
        return httpConnectionGaugeName(clientName, ".leased-connections");
    }

    private static String maxConnectionsGaugeName(String clientName) {
        return httpConnectionGaugeName(clientName, ".max-connections");
    }

    private static String httpConnectionGaugeName(String clientName, String type) {
        return HTTP_CONN_MANAGER_GAUGE_PREFIX + clientName + type;
    }

    @Value
    @VisibleForTesting
    static class ClientConnectionInfo {
        String clientName;
        int leased;
        int max;
        double warningThreshold;
        double percentLeased;

        enum HealthStatus {
            HEALTHY, UNHEALTHY
        }

        @Builder
        ClientConnectionInfo(String clientName, int leased, int max, double warningThreshold) {
            this.clientName = clientName;
            this.leased = leased;
            this.max = max;
            this.warningThreshold = warningThreshold;
            this.percentLeased = 100.0 * (leased / (double) max);
        }

        boolean isUnhealthy() {
            return percentLeased >= warningThreshold;
        }

        HealthStatus getHealthStatus() {
            return isUnhealthy() ? HealthStatus.UNHEALTHY : HealthStatus.HEALTHY;
        }
    }
}
