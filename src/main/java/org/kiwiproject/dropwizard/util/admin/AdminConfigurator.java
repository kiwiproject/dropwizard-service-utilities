package org.kiwiproject.dropwizard.util.admin;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.config.HttpHealthCheckConfig;
import org.kiwiproject.dropwizard.util.config.KeystoreConfig;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;
import org.kiwiproject.dropwizard.util.health.keystore.ExpiringKeystoreHealthCheck;
import org.kiwiproject.dropwizard.util.metrics.ServerLoadGauge;
import org.kiwiproject.dropwizard.util.resource.ConfigResource;
import org.kiwiproject.dropwizard.util.task.ServerLoadTask;

import java.util.List;
import java.util.stream.Stream;

/**
 * A utility to configure and setup various "standard" settings for a Dropwizard service. Standard in this case is very
 * opinionated to this library.
 */
public class AdminConfigurator {

    private final Environment environment;

    private boolean shouldIncludeServerLoadTask;
    private boolean shouldIncludeServerLoadMetric;
    private boolean shouldIncludeHttpConnectionsHealthCheck;
    private boolean shouldIncludeExpiringKeystoreHealthCheck;
    private boolean shouldIncludeConfigResource;

    private TlsContextConfiguration tlsConfiguration;
    private HttpHealthCheckConfig httpHealthCheckConfig;
    private List<String> hiddenFieldRegex;
    private Configuration config;

    /**
     * Creates a new {@link AdminConfigurator} with a given Dropwizard {@link Environment}
     *
     * @param environment the Dropwizard environment to use
     */
    public AdminConfigurator(Environment environment) {
        this.environment = requireNotNull(environment, "environment is required");
    }

    /**
     * Enables setup of the {@link ServerLoadTask} on the service.
     *
     * @return this configurator
     * @see ServerLoadTask
     */
    public AdminConfigurator withServerLoadTask() {
        this.shouldIncludeServerLoadTask = true;
        return this;
    }

    /**
     * Enables setup of the {@link ServerLoadGauge} on the service.
     *
     * @return this configurator
     * @see ServerLoadGauge
     */
    public AdminConfigurator withServerLoadMetric() {
        this.shouldIncludeServerLoadMetric = true;
        return this;
    }

    /**
     * Enables setup of the {@link HttpConnectionsHealthCheck} on the service with the default warning threshold.
     *
     * @return this configurator
     * @see HttpConnectionsHealthCheck
     */
    public AdminConfigurator withHttpConnectionsHealthCheck() {
        return withHttpConnectionsHealthCheck(HttpHealthCheckConfig.builder().build());
    }

    /**
     * Enables setup of the {@link HttpConnectionsHealthCheck} on the service with the given warning threshold.
     *
     * @param httpHealthCheckConfig the configuration to use for the {@link HttpConnectionsHealthCheck}
     * @return this configurator
     * @see HttpConnectionsHealthCheck
     */
    public AdminConfigurator withHttpConnectionsHealthCheck(HttpHealthCheckConfig httpHealthCheckConfig) {
        this.shouldIncludeHttpConnectionsHealthCheck = true;
        this.httpHealthCheckConfig = httpHealthCheckConfig;
        return this;
    }

    /**
     * Enables setup of the {@link ExpiringKeystoreHealthCheck} on the service with the given
     * {@link TlsContextConfiguration}.
     *
     * @param tlsConfiguration the {@link TlsContextConfiguration} needed to set up the health check
     * @return this configurator
     * @throws IllegalArgumentException if tlsConfiguration is null
     * @see ExpiringKeystoreHealthCheck
     */
    public AdminConfigurator withExpiringKeystoreHealthCheck(TlsContextConfiguration tlsConfiguration) {
        this.shouldIncludeExpiringKeystoreHealthCheck = true;
        this.tlsConfiguration = requireNotNull(tlsConfiguration, "tlsConfiguration is required");
        return this;
    }

    /**
     * Enables setup of the {@link ConfigResource} on the service with the given config and optional list of
     * regex patterns to exclude from the output.
     *
     * @param config           the configuration for the service
     * @param hiddenFieldRegex list of regex patterns to match for sensitive fields in the config (e.g. passwords)
     * @param <T>              the type of the Dropwizard configuration class
     * @return this configurator
     * @throws IllegalArgumentException if config is null
     * @see ConfigResource
     */
    public <T extends Configuration> AdminConfigurator withConfigResource(T config, List<String> hiddenFieldRegex) {
        this.shouldIncludeConfigResource = true;
        this.hiddenFieldRegex = hiddenFieldRegex;
        this.config = requireNotNull(config, "config is required");
        return this;
    }

    /**
     * Terminal operation of this configurator that adds all of the configured features into the server setup.
     */
    public void configure() {
        // Add "standard" tasks
        addServerLoadTask();

        // Add "standard" metrics
        addServerLoadMetric();

        // Add "standard" healthchecks
        addHttpConnectionsHealthCheck();
        addExpiringKeystoreHealthCheck();

        // Add "standard" resources
        addConfigResource();
    }

    private void addServerLoadTask() {
        if (shouldIncludeServerLoadTask) {
            environment.admin().addTask(new ServerLoadTask());
        }
    }

    private void addServerLoadMetric() {
        if (shouldIncludeServerLoadMetric) {
            var metrics = environment.metrics();
            var metricName = MetricRegistry.name(ServerLoadGauge.NAME);

            metrics.register(metricName, new ServerLoadGauge());
        }
    }

    private void addHttpConnectionsHealthCheck() {
        if (shouldIncludeHttpConnectionsHealthCheck) {
            environment.healthChecks().register(httpHealthCheckConfig.getName(),
                    new HttpConnectionsHealthCheck(environment.metrics(), httpHealthCheckConfig.getWarningThreshold()));
        }
    }

    private void addExpiringKeystoreHealthCheck() {
        if (shouldIncludeExpiringKeystoreHealthCheck) {
            registerExpiringKeystoreHealthCheck();
        }
    }

    private void registerExpiringKeystoreHealthCheck() {
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
                .filter(tlsConfig -> isNotBlank(tlsConfig.getPath()))
                .forEach(tlsConfig -> environment.healthChecks()
                        .register(tlsConfig.getName(), new ExpiringKeystoreHealthCheck(tlsConfig)));
    }

    private void addConfigResource() {
        if (shouldIncludeConfigResource) {
            environment.jersey().register(new ConfigResource(config, hiddenFieldRegex));
        }
    }
}
