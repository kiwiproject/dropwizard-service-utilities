package org.kiwiproject.dropwizard.util.lifecycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.management.dropwizard.RegistrationLifecycleListener;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

@DisplayName("StandardLifecycles")
class StandardLifecyclesTest {

    @Nested
    class AddServiceRunningLifecycleListener {

        @Test
        void shouldAddListener() {
            var serviceInfo = mock(ServiceInfo.class);
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addServiceRunningLifecycleListener(serviceInfo, environment);

            verify(environment.lifecycle()).addServerLifecycleListener(any());
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

            verify(environment.lifecycle()).addLifeCycleListener(any(RegistrationLifecycleListener.class));
            verify(environment.lifecycle()).addServerLifecycleListener(any(RegistrationLifecycleListener.class));
        }
    }

    @Nested
    class AddServerConnectorLoggingLifecycleListener {

        @Test
        void shouldAddConnectorLoggingServerLifecycleListener() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addServerConnectorLoggingLifecycleListener(environment);

            verify(environment.lifecycle()).addServerLifecycleListener(any(ConnectorLoggingServerLifecycleListener.class));
        }

    }

    @Nested
    class AddProcessIdLoggingLifecycleListener {

        @Test
        void shouldAddProcessIdLoggingLifecycleListener() {
            var environment = DropwizardMockitoMocks.mockEnvironment();

            StandardLifecycles.addProcessIdLoggingLifecycleListener(10_000, environment);

            verify(environment.lifecycle()).addServerLifecycleListener(any(ProcessIdLoggingServerLifecycleListener.class));
        }

    }

}
