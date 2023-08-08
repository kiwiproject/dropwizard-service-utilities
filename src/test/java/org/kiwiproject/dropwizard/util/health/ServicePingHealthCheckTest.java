package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.util.health.ServicePingHealthCheck.DependencyImportance;
import org.kiwiproject.jersey.client.ClientBuilders;
import org.kiwiproject.jersey.client.RegistryAwareClient;
import org.kiwiproject.jersey.client.ServiceIdentifier;
import org.kiwiproject.metrics.health.HealthStatus;
import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.client.RegistryClient.InstanceQuery;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;
import org.kiwiproject.registry.model.Port.Security;
import org.kiwiproject.registry.model.ServiceInstance;
import org.kiwiproject.registry.model.ServicePaths;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

@DisplayName("ServicePingHealthCheck")
@ExtendWith(DropwizardExtensionsSupport.class)
class ServicePingHealthCheckTest {

    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public static class TestRemoteServiceResource {
        @GET
        @Path("pingGood")
        public Response ping200() {
            return Response.ok("pong").build();
        }

        @GET
        @Path("pingNotOKStatus")
        public Response ping400() {
            return Response.status(400).build();
        }
    }

    private static final DropwizardClientExtension CLIENT = new DropwizardClientExtension(new TestRemoteServiceResource());

    @Nested
    class IsHealthy {

        @Test
        void whenServiceIsFoundAndPingReturnsSuccessfully() {
            var identifier = ServiceIdentifier.builder().serviceName("test-service").build();
            var registryClient = mock(RegistryClient.class);
            var client = ClientBuilders.jersey().registryClient(registryClient).build();

            var healthCheck = new ServicePingHealthCheck(identifier, client);

            var port = CLIENT.baseUri().getPort();
            var basePath = CLIENT.baseUri().getPath();

            var instance = ServiceInstance.builder()
                    .hostName("localhost")
                    .ports(List.of(Port.of(port, PortType.ADMIN, Security.NOT_SECURE)))
                    .paths(ServicePaths.builder().statusPath(basePath + "/pingGood").build())
                    .build();

            when(registryClient.findServiceInstanceBy(any(InstanceQuery.class))).thenReturn(Optional.of(instance));

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasDetail("severity", HealthStatus.OK.name())
                    .hasDetail("pingUri", CLIENT.baseUri() + "/pingGood")
                    .hasDetail("importance", DependencyImportance.REQUIRED.name());
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenServiceCannotBeFound() {
            var identifier = ServiceIdentifier.builder().serviceName("test-service").build();
            var registryClient = mock(RegistryClient.class);
            var client = ClientBuilders.jersey().registryClient(registryClient).build();

            var healthCheck = new ServicePingHealthCheck(identifier, client);

            when(registryClient.findServiceInstanceBy(any(InstanceQuery.class))).thenReturn(Optional.empty());

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage("Service test-service not found")
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .hasDetail("pingUri", "unknown")
                    .hasDetail("importance", DependencyImportance.REQUIRED.name());
        }

        @Test
        void whenServicePingReturnsNonOkStatus() {
            var identifier = ServiceIdentifier.builder().serviceName("test-service").build();
            var registryClient = mock(RegistryClient.class);
            var client = ClientBuilders.jersey().registryClient(registryClient).build();

            var healthCheck = new ServicePingHealthCheck(identifier, client);

            var port = CLIENT.baseUri().getPort();
            var basePath = CLIENT.baseUri().getPath();

            var instance = ServiceInstance.builder()
                    .hostName("localhost")
                    .ports(List.of(Port.of(port, PortType.ADMIN, Security.NOT_SECURE)))
                    .paths(ServicePaths.builder().statusPath(basePath + "/pingNotOKStatus").build())
                    .build();

            when(registryClient.findServiceInstanceBy(any(InstanceQuery.class))).thenReturn(Optional.of(instance));

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessageStartingWith("Ping to service test-service at ")
                    .hasMessageEndingWith("returned non-OK status 400")
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .hasDetail("pingUri", CLIENT.baseUri() + "/pingNotOKStatus")
                    .hasDetail("importance", DependencyImportance.REQUIRED.name());
        }
    }

    @Nested
    class RegisterServiceChecks {

        @Test
        void shouldRegisterAllHealthChecksForTheGivenServiceIdentifiersWithDefaultImportance() {
            var registry = mock(HealthCheckRegistry.class);
            var client = mock(RegistryAwareClient.class);

            var identifiers = new ServiceIdentifier[] {
                    ServiceIdentifier.builder().serviceName("service-one").build(),
                    ServiceIdentifier.builder().serviceName("service-two").build()
            };

            ServicePingHealthCheck.registerServiceChecks(registry, client, identifiers);

            var captorOne = ArgumentCaptor.forClass(ServicePingHealthCheck.class);
            verify(registry).register(eq("Ping: Service One"), captorOne.capture());

            var checkOne = captorOne.getValue();
            assertThat(checkOne.dependencyImportance).isEqualTo(DependencyImportance.REQUIRED);

            var captorTwo = ArgumentCaptor.forClass(ServicePingHealthCheck.class);
            verify(registry).register(eq("Ping: Service Two"), captorTwo.capture());

            var checkTwo = captorTwo.getValue();
            assertThat(checkTwo.dependencyImportance).isEqualTo(DependencyImportance.REQUIRED);
        }

        @Test
        void shouldRegisterAllHealthChecksForTheGivenServiceIdentifiersWithGivenImportance() {
            var registry = mock(HealthCheckRegistry.class);
            var client = mock(RegistryAwareClient.class);

            var identifiers = new ServiceIdentifier[] {
                    ServiceIdentifier.builder().serviceName("service-one").build(),
                    ServiceIdentifier.builder().serviceName("service-two").build()
            };

            ServicePingHealthCheck.registerServiceChecks(registry, client, DependencyImportance.INFORMATIONAL, identifiers);

            var captorOne = ArgumentCaptor.forClass(ServicePingHealthCheck.class);
            verify(registry).register(eq("Ping: Service One"), captorOne.capture());

            var checkOne = captorOne.getValue();
            assertThat(checkOne.dependencyImportance).isEqualTo(DependencyImportance.INFORMATIONAL);

            var captorTwo = ArgumentCaptor.forClass(ServicePingHealthCheck.class);
            verify(registry).register(eq("Ping: Service Two"), captorTwo.capture());

            var checkTwo = captorTwo.getValue();
            assertThat(checkTwo.dependencyImportance).isEqualTo(DependencyImportance.INFORMATIONAL);
        }

        @Test
        void shouldNotRegisterAnyHealthChecksWhenNoIdentifiers() {
            var registry = mock(HealthCheckRegistry.class);
            var client = mock(RegistryAwareClient.class);
            var identifiers = new ServiceIdentifier[0];

            ServicePingHealthCheck.registerServiceChecks(registry, client, identifiers);

            verifyNoInteractions(registry);
        }
    }
}
