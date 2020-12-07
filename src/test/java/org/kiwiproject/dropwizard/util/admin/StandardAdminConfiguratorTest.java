package org.kiwiproject.dropwizard.util.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.setup.AdminEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;
import org.kiwiproject.dropwizard.util.health.keystore.ExpiringKeystoreHealthCheck;
import org.kiwiproject.dropwizard.util.metrics.ServerLoadGauge;
import org.kiwiproject.dropwizard.util.task.ServerLoadTask;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

@DisplayName("StandardAdminConfigurations")
class StandardAdminConfiguratorTest {

    @Nested
    class Construct {

        @Test
        void shouldRequireEnvironment_WhenUsingBuilder() {
            var builder = StandardAdminConfigurator.builder();
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Environment is required");
        }

        @Test
        void shouldDefaultLeasedWarningThreshold_WhenNotGiven() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            assertThat(configurator.getLeasedWarningThreshold())
                    .isEqualTo(HttpConnectionsHealthCheck.DEFAULT_WARNING_THRESHOLD);
        }

        @Test
        void shouldDefaultRegisterExpiringKeystoreHealthCheck_ToTrue() {
            var environment = DropwizardMockitoMocks.mockEnvironment();
            var tlsConfig = new TlsContextConfiguration();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .tlsConfiguration(tlsConfig)
                    .build();

            assertThat(configurator.isRegisterExpiringKeystoreHealthCheck()).isTrue();
        }

        @Test
        void shouldRequireTlsContextConfiguration_WhenRegisterExpiringHealthCheck_IsTrue() {
            var environment = DropwizardMockitoMocks.mockEnvironment();
            var builder = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(true);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TlsConfiguration is required when registering ExpiringKeystoreHealthCheck");
        }

        @Test
        void shouldDefaultRegisterConfigResource_ToTrue() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            assertThat(configurator.isRegisterConfigResource()).isTrue();
        }
    }

    @Nested
    class AddTasks {

        @Test
        void shouldAddTheServerLoadTask() {
            var environment = DropwizardMockitoMocks.mockEnvironment();
            var adminEnvironment = mock(AdminEnvironment.class);
            when(environment.admin()).thenReturn(adminEnvironment);

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            configurator.addTasks();

            verify(environment.admin()).addTask(any(ServerLoadTask.class));
        }
    }

    @Nested
    class AddMetrics {

        @Test
        void shouldAddServerLoadGauge() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            configurator.addMetrics();

            verify(environment.metrics()).register(eq(ServerLoadGauge.NAME), any(ServerLoadGauge.class));
        }
    }

    @Nested
    class AddHealthChecks {

        @Test
        void shouldAddHttpConnectionsHealthCheck() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            configurator.addHealthChecks();

            verify(environment.healthChecks()).register(eq(HttpConnectionsHealthCheck.DEFAULT_NAME), any(HttpConnectionsHealthCheck.class));
        }

        @Test
        void shouldNotAddExpiringKeystoreHealthCheck_WhenNotConfigured() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .registerExpiringKeystoreHealthCheck(false)
                    .build();

            configurator.addHealthChecks();

            verify(environment.healthChecks(), times(0)).register(any(String.class), any(ExpiringKeystoreHealthCheck.class));
        }

        @Test
        void shouldAddExpiringKeystoreHealthCheck_ForKeyStoreAndTrustStore_WhenConfigured() {
            var environment = DropwizardMockitoMocks.mockEnvironment();
            var tlsConfiguration = TlsContextConfiguration.builder()
                    .keyStorePath("/path/to/keystore")
                    .keyStorePassword("password")
                    .trustStorePath("/path/to/truststore")
                    .trustStorePassword("password")
                    .build();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .tlsConfiguration(tlsConfiguration)
                    .build();

            configurator.addHealthChecks();

            verify(environment.healthChecks()).register(eq("Key store"), any(ExpiringKeystoreHealthCheck.class));
            verify(environment.healthChecks()).register(eq("Trust store"), any(ExpiringKeystoreHealthCheck.class));
        }

        @Test
        void shouldAddExpiringKeystoreHealthCheck_ForTrustStoreOnly_WhenConfiguredAndKeyStorePathIsMissing() {
            var environment = DropwizardMockitoMocks.mockEnvironment();
            var tlsConfiguration = TlsContextConfiguration.builder()
                    .trustStorePath("/path/to/truststore")
                    .trustStorePassword("password")
                    .build();

            var configurator = StandardAdminConfigurator.builder()
                    .environment(environment)
                    .tlsConfiguration(tlsConfiguration)
                    .build();

            configurator.addHealthChecks();

            verify(environment.healthChecks()).register(eq("Trust store"), any(ExpiringKeystoreHealthCheck.class));
            verify(environment.healthChecks(), times(0)).register(eq("Key store"), any(ExpiringKeystoreHealthCheck.class));
        }
    }
}
