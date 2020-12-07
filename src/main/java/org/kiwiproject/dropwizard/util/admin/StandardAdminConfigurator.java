package org.kiwiproject.dropwizard.util.admin;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.setup.Environment;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.config.KeystoreConfig;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;
import org.kiwiproject.dropwizard.util.health.keystore.ExpiringKeystoreHealthCheck;
import org.kiwiproject.dropwizard.util.metrics.ServerLoadGauge;
import org.kiwiproject.dropwizard.util.task.ServerLoadTask;

import java.util.stream.Stream;

/**
 * Set of utilities to assist in configuring administrative functions of a Dropwizard service.
 */
public class StandardAdminConfigurator {

    private final Environment environment;
    private TlsContextConfiguration tlsConfiguration;

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final Double leasedWarningThreshold;

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final boolean registerExpiringKeystoreHealthCheck;

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final boolean registerConfigResource;

    @Builder
    private StandardAdminConfigurator(Environment environment,
                                      Double leasedWarningThreshold,
                                      TlsContextConfiguration tlsConfiguration,
                                      Boolean registerExpiringKeystoreHealthCheck,
                                      Boolean registerConfigResource) {

        this.environment = requireNotNull(environment, "Environment is required");
        this.leasedWarningThreshold = isNull(leasedWarningThreshold) ? HttpConnectionsHealthCheck.DEFAULT_WARNING_THRESHOLD : leasedWarningThreshold;
        this.registerExpiringKeystoreHealthCheck = isNull(registerExpiringKeystoreHealthCheck) || registerExpiringKeystoreHealthCheck;
        this.registerConfigResource = isNull(registerConfigResource) || registerConfigResource;

        if (this.registerExpiringKeystoreHealthCheck) {
            this.tlsConfiguration = requireNotNull(tlsConfiguration,
                    "TlsConfiguration is required when registering ExpiringKeystoreHealthCheck");
        }
    }

    /**
     * Sets up a task to retrieve the server load for the machine the service is running on.
     *
     * @see ServerLoadTask
     */
    public void addTasks() {
        environment.admin().addTask(new ServerLoadTask());
    }

    /**
     * Adds the {@link ServerLoadGauge} metric to the {@link MetricRegistry} to track server load.
     *
     * @see ServerLoadGauge
     */
    public void addMetrics() {
        var metrics = environment.metrics();
        var metricName = MetricRegistry.name(ServerLoadGauge.NAME);

        metrics.register(metricName, new ServerLoadGauge());
    }

    /**
     * Adds various default Health Checks to the service.
     * <ul>
     *     <li>{@link HttpConnectionsHealthCheck}</li>
     *     <li>{@link ExpiringKeystoreHealthCheck} (if configured to do so)</li>
     * </ul>
     */
    public void addHealthChecks() {
        var healthChecks = environment.healthChecks();

        healthChecks.register(HttpConnectionsHealthCheck.DEFAULT_NAME,
                new HttpConnectionsHealthCheck(environment.metrics(), leasedWarningThreshold));

        if (registerExpiringKeystoreHealthCheck) {
            registerExpiringKeystoreHealthCheck(healthChecks);
        }
    }

    private void registerExpiringKeystoreHealthCheck(HealthCheckRegistry healthChecks) {
        var keyStore = KeystoreConfig.builder()
                .name("Key store")
                .path(tlsConfiguration.getKeyStorePath())
                .pass(tlsConfiguration.getKeyStorePassword())
                .build();

        var trustStore = KeystoreConfig.builder()
                .name("Trust store")
                .path(tlsConfiguration.getTrustStorePath())
                .pass(tlsConfiguration.getTrustStorePassword())
                .build();

        Stream.of(keyStore, trustStore)
                .filter(config -> isNotBlank(config.getPath()))
                .forEach(config -> healthChecks.register(config.getName(), new ExpiringKeystoreHealthCheck(config)));
    }
}
