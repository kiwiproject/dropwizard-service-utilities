package org.kiwiproject.dropwizard.util.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.Configuration;
import io.dropwizard.setup.AdminEnvironment;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;
import org.kiwiproject.dropwizard.util.health.keystore.ExpiringKeystoreHealthCheck;
import org.kiwiproject.dropwizard.util.metrics.ServerLoadGauge;
import org.kiwiproject.dropwizard.util.resource.ConfigResource;
import org.kiwiproject.dropwizard.util.task.ServerLoadTask;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.util.List;

@DisplayName("StandardAdminConfigurations")
class AdminConfiguratorTest {

    private Environment environment;

    @BeforeEach
    void setUp() {
        environment = DropwizardMockitoMocks.mockEnvironment();
    }

    @Nested
    class IncludeServerLoadTask {

        @Test
        void shouldAddTheServerLoadTask() {
            var adminEnvironment = mock(AdminEnvironment.class);
            when(environment.admin()).thenReturn(adminEnvironment);

            new AdminConfigurator<>(environment)
                    .includeServerLoadTask()
                    .configure();

            verify(environment.admin()).addTask(any(ServerLoadTask.class));
        }
    }

    @Nested
    class IncludeServerLoadMetric {

        @Test
        void shouldAddServerLoadGauge() {
            new AdminConfigurator<>(environment)
                    .includeServerLoadMetric()
                    .configure();

            verify(environment.metrics()).register(eq(ServerLoadGauge.NAME), any(ServerLoadGauge.class));
        }
    }

    @Nested
    class IncludeHttpConnectionsHealthCheck {

        @Test
        void shouldAddHttpConnectionsHealthCheck_WithDefault() {
            new AdminConfigurator<>(environment)
                    .includeHttpConnectionsHealthCheck()
                    .configure();

            verify(environment.healthChecks())
                    .register(eq(HttpConnectionsHealthCheck.DEFAULT_NAME), any(HttpConnectionsHealthCheck.class));
        }

        @Test
        void shouldAddHttpConnectionsHealthCheck_WithGivenThreshold() {
            new AdminConfigurator<>(environment)
                    .includeHttpConnectionsHealthCheck(60.0)
                    .configure();

            verify(environment.healthChecks())
                    .register(eq(HttpConnectionsHealthCheck.DEFAULT_NAME), any(HttpConnectionsHealthCheck.class));
        }
    }

    @Nested
    class IncludeExpiringKeystoreHealthCheck {

        @Test
        void shouldThrowIllegalArgumentException_WhenTlsConfigIsNull() {
            var configurator = new AdminConfigurator<>(environment);

            assertThatThrownBy(() -> configurator.includeExpiringKeystoreHealthCheck(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("tlsConfiguration is required");
        }

        @Test
        void shouldAddExpiringKeystoreHealthCheck_ForKeyStoreAndTrustStore_WhenConfigured() {
            var tlsConfiguration = TlsContextConfiguration.builder()
                    .keyStorePath("/path/to/keystore")
                    .keyStorePassword("password")
                    .trustStorePath("/path/to/truststore")
                    .trustStorePassword("password")
                    .build();

            new AdminConfigurator<>(environment)
                    .includeExpiringKeystoreHealthCheck(tlsConfiguration)
                    .configure();

            verify(environment.healthChecks()).register(eq("Key store"), any(ExpiringKeystoreHealthCheck.class));
            verify(environment.healthChecks()).register(eq("Trust store"), any(ExpiringKeystoreHealthCheck.class));
        }

        @Test
        void shouldAddExpiringKeystoreHealthCheck_ForTrustStoreOnly_WhenConfiguredAndKeyStorePathIsMissing() {
            var tlsConfiguration = TlsContextConfiguration.builder()
                    .trustStorePath("/path/to/truststore")
                    .trustStorePassword("password")
                    .build();

            new AdminConfigurator<>(environment)
                    .includeExpiringKeystoreHealthCheck(tlsConfiguration)
                    .configure();

            verify(environment.healthChecks()).register(eq("Trust store"), any(ExpiringKeystoreHealthCheck.class));
            verify(environment.healthChecks(), times(0))
                    .register(eq("Key store"), any(ExpiringKeystoreHealthCheck.class));
        }
    }

    @Nested
    class IncludeConfigResource {

        @Test
        void shouldThrowIllegalArgumentException_WhenConfigIsNull() {
            var configurator = new AdminConfigurator<>(environment);

            assertThatThrownBy(() -> configurator.includeConfigResource(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("config is required");
        }

        @Test
        void shouldAddConfigResource() {
            var config = mock(Configuration.class);

            new AdminConfigurator<>(environment)
                    .includeConfigResource(config, List.of())
                    .configure();

            verify(environment.jersey()).register(any(ConfigResource.class));
        }
    }
}
