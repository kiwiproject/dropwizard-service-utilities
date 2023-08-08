package org.kiwiproject.dropwizard.util.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.NoopMetricRegistry;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.concurrent.TestExecutors;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.management.dropwizard.RegistrationLifecycleListener;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;

@DisplayName("StandardLifecycles")
class StandardLifecyclesTest {

    @Nested
    class AddServiceRunningLifecycleListener {

        @Test
        void shouldAddListener() {
            var serviceInfo = mock(ServiceInfo.class);
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addServiceRunningLifecycleListener(serviceInfo, environment);

            verify(environment.lifecycle()).addServerLifecycleListener(any(ServerStatusServerLifecycleListener.class));
        }

    }

    @Nested
    class AddRegistryLifecycleListeners {

        @Test
        void shouldAddLifecycleListenerForStartup_AndServerLifecycleListenerForShutdown() {
            var registryService = mock(RegistryService.class);
            var serviceInfo = mock(ServiceInfo.class);
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addRegistryLifecycleListeners(registryService, serviceInfo, environment);

            verify(environment.lifecycle()).addEventListener(any(RegistrationLifecycleListener.class));
            verify(environment.lifecycle()).addServerLifecycleListener(any(RegistrationLifecycleListener.class));
        }
    }

    @Nested
    class AddServerConnectorLoggingLifecycleListener {

        @Test
        void shouldAddConnectorLoggingServerLifecycleListener() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addServerConnectorLoggingLifecycleListener(environment);

            verify(environment.lifecycle())
                    .addServerLifecycleListener(any(ConnectorLoggingServerLifecycleListener.class));
        }

    }

    @Nested
    class AddProcessIdLoggingLifecycleListener {

        private Environment environment;

        @BeforeEach
        void setUp() {
            environment = DropwizardMockitoMocks.mockEnvironment();
        }

        @Test
        void shouldAddProcessIdLoggingLifecycleListener_FromPrimitiveLong() {
            StandardLifecycles.addProcessIdLoggingLifecycleListener(10_000L, environment);

            verifyListenerAdded();
        }

        @Test
        void shouldAddProcessIdLoggingLifecycleListener_FromWrapperLong() {
            StandardLifecycles.addProcessIdLoggingLifecycleListener(Long.valueOf(20_000L), environment);

            verifyListenerAdded();
        }

        @Test
        void shouldAddProcessIdLoggingLifecycleListener_FromWrapperLong_EvenWhenNull() {
            StandardLifecycles.addProcessIdLoggingLifecycleListener((Long) null, environment);

            verifyListenerAdded();
        }

        @Test
        void shouldAddProcessIdLoggingLifecycleListener_FromOptionalLong() {
            StandardLifecycles.addProcessIdLoggingLifecycleListener(OptionalLong.of(30_000L), environment);

            verifyListenerAdded();
        }

        @Test
        void shouldAddProcessIdLoggingLifecycleListener_FromOptionalLong_EvenWhenEmpty() {
            StandardLifecycles.addProcessIdLoggingLifecycleListener(OptionalLong.empty(), environment);

            verifyListenerAdded();
        }

        private void verifyListenerAdded() {
            verify(environment.lifecycle())
                    .addServerLifecycleListener(any(ProcessIdLoggingServerLifecycleListener.class));
        }
    }

    @Nested
    class NewScheduledExecutor {

        @Test
        void shouldRequireEnvironment() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StandardLifecycles.newScheduledExecutor(null, "My Executor"));
        }

        @Test
        void shouldRequireName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StandardLifecycles.newScheduledExecutor(mock(Environment.class), ""));
        }

        @Test
        void shouldBuildScheduledExecutorService() throws InterruptedException {
            var lifecycleEnvironment = new LifecycleEnvironment(new NoopMetricRegistry());
            var environment = mock(Environment.class);
            when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
            var name = "My Custom Executor";

            TestExecutors.use(StandardLifecycles.newScheduledExecutor(environment, name), executor -> {
                assertThat(executor).isInstanceOf(ScheduledExecutorService.class);
                assertThat(executor.isShutdown()).isFalse();
                assertThat(executor.isTerminated()).isFalse();
            });
        }
    }

}
