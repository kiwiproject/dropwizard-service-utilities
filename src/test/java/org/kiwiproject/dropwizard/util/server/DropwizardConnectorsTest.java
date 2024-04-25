package org.kiwiproject.dropwizard.util.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors.ConnectorType;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@DisplayName("DropwizardConnectors")
class DropwizardConnectorsTest {

    @Nested
    class RequireDefaultServerFactory {
        @Test
        void throwsIllegalStateException_WhenServerFactory_IsNotInstanceOfDefaultServerFactory() {
            ServerFactory factory = new SimpleServerFactory();
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory(factory))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("The server factory is not a %s (it is a ", DefaultServerFactory.class.getName())
                    .hasMessageEndingWith("SimpleServerFactory)");
        }

        @Test
        void throwsIllegalArgumentException_WhenConfiguration_IsNull() {
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory((Configuration) null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("configuration is required");
        }

        @Test
        void throwsIllegalArgumentException_WhenServerFactory_IsNull() {
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory((DefaultServerFactory) null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("ServerFactory is required");
        }

        @Test
        void shouldReturnGivenServerFactory_WhenInstanceOfDefaultServerFactory() {
            var factory = new DefaultServerFactory();

            var checkedFactory = DropwizardConnectors.requireDefaultServerFactory(factory);

            assertThat(checkedFactory).isSameAs(factory);
        }
    }

    @Nested
    class GetAllPorts {

        @Test
        void shouldFindAllPorts() {
            var serverFactory = new DefaultServerFactory();
            var httpsAppConnector = newConnectorFactory(ConnectorType.HTTPS, 9090);
            var httpAppConnector = newConnectorFactory(ConnectorType.HTTP, 19000);
            var httpsAdminConnector = newConnectorFactory(ConnectorType.HTTPS, 9091);
            var httpAdminConnector = newConnectorFactory(ConnectorType.HTTP, 19001);
            serverFactory.setApplicationConnectors(List.of(httpsAppConnector, httpAppConnector));
            serverFactory.setAdminConnectors(List.of(httpsAdminConnector, httpAdminConnector));

            var config = newConfiguration(serverFactory);

            var ports = DropwizardConnectors.getPorts(config);

            assertThat(ports)
                    .describedAs("Application ports should come first, and HTTP ports should be listed first")
                    .extracting(Port::getNumber)
                    .containsExactly(19000, 9090, 19001, 9091);
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getPorts(config));
        }
    }

    @Nested
    class GetOnlyApplicationPort {

        @Test
        void shouldGetTheSingleApplicationPort() {
            var serverFactory = newDefaultServerFactory(ConnectorType.HTTPS, 25000, 50000);
            var config = newConfiguration(serverFactory);

            var port = DropwizardConnectors.getOnlyApplicationPort(config);

            assertAll(
                () -> assertThat(port.getNumber()).isEqualTo(25000),
                () -> assertThat(port.getType()).isEqualTo(PortType.APPLICATION),
                () -> assertThat(port.getSecure()).isEqualTo(Port.Security.SECURE)
            );
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneApplicationPort() {
            var serverFactory = new DefaultServerFactory();
            var httpsAppConnector = newConnectorFactory(ConnectorType.HTTPS, 9090);
            var httpAppConnector = newConnectorFactory(ConnectorType.HTTP, 19000);
            var httpAdminConnector = newConnectorFactory(ConnectorType.HTTP, 19001);
            serverFactory.setApplicationConnectors(List.of(httpsAppConnector, httpAppConnector));
            serverFactory.setAdminConnectors(List.of(httpAdminConnector));

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getOnlyApplicationPort(serverFactory));
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getOnlyApplicationPort(config));
        }
    }

    @Nested
    class GetApplicationPorts {

        @Test
        void shouldFindOnlyAdminPorts() {
            var applicationPortNumber = 9090;
            var adminPortNumber = 9095;
            var serverFactory = newDefaultServerFactory(ConnectorType.HTTPS, applicationPortNumber, adminPortNumber);
            var config = newConfiguration(serverFactory);

            var adminPorts = DropwizardConnectors.getApplicationPorts(config);
            assertThat(adminPorts).extracting(Port::getNumber).containsExactly(applicationPortNumber);
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getApplicationPorts(config));
        }
    }

    @Nested
    class GetOnlyAdminPort {

        @Test
        void shouldGetTheSingleAdminPort() {
            var serverFactory = newDefaultServerFactory(ConnectorType.HTTP, 25000, 50000);
            var config = newConfiguration(serverFactory);

            var port = DropwizardConnectors.getOnlyAdminPort(config);

            assertAll(
                () -> assertThat(port.getNumber()).isEqualTo(50000),
                () -> assertThat(port.getType()).isEqualTo(PortType.ADMIN),
                () -> assertThat(port.getSecure()).isEqualTo(Port.Security.NOT_SECURE)
            );
        }

        @Test
        void shouldThrowIllegalStateWhenMoreThanOneAdminPort() {
            var serverFactory = new DefaultServerFactory();
            var httpsAppConnector = newConnectorFactory(ConnectorType.HTTPS, 9090);
            var httpsAdminConnector = newConnectorFactory(ConnectorType.HTTPS, 19000);
            var httpAdminConnector = newConnectorFactory(ConnectorType.HTTP, 19001);
            serverFactory.setApplicationConnectors(List.of(httpsAppConnector));
            serverFactory.setAdminConnectors(List.of(httpsAdminConnector, httpAdminConnector));

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getOnlyAdminPort(serverFactory));
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getOnlyAdminPort(config));
        }
    }

    @Nested
    class GetAdminPorts {

        @Test
        void shouldFindOnlyAdminPorts() {
            var applicationPortNumber = 9090;
            var adminPortNumber = 9095;
            var serverFactory = newDefaultServerFactory(ConnectorType.HTTPS, applicationPortNumber, adminPortNumber);
            var config = newConfiguration(serverFactory);

            var adminPorts = DropwizardConnectors.getAdminPorts(config);
            assertThat(adminPorts).extracting(Port::getNumber).containsExactly(adminPortNumber);
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getAdminPorts(config));
        }
    }

    @Nested
    class GetPortsOfType {

        @ParameterizedTest
        @EnumSource(ConnectorType.class)
        void shouldFindExpectedPorts(ConnectorType connectorType) {
            var applicationPortNumber = 9090;
            var adminPortNumber = 9095;
            var serverFactory = newDefaultServerFactory(connectorType, applicationPortNumber, adminPortNumber);
            var config = newConfiguration(serverFactory);

            assertAll(
                () -> assertThat(DropwizardConnectors.getPorts(config, PortType.APPLICATION))
                        .extracting(Port::getNumber)
                        .containsExactly(applicationPortNumber),
                () -> assertThat(DropwizardConnectors.getPorts(config, PortType.ADMIN))
                        .extracting(Port::getNumber)
                        .containsExactly(adminPortNumber)
            );
        }

        @Test
        void shouldFindAllPorts() {
            var serverFactory = new DefaultServerFactory();
            var httpsAppConnector = newConnectorFactory(ConnectorType.HTTPS, 9090);
            var httpAppConnector = newConnectorFactory(ConnectorType.HTTP, 19000);
            var httpAdminConnector = newConnectorFactory(ConnectorType.HTTP, 19001);
            serverFactory.setApplicationConnectors(List.of(httpsAppConnector, httpAppConnector));
            serverFactory.setAdminConnectors(List.of(httpAdminConnector));

            var applicationPorts = DropwizardConnectors.getPorts(serverFactory, PortType.APPLICATION);
            var adminPorts = DropwizardConnectors.getPorts(serverFactory, PortType.ADMIN);

            assertAll(
                () -> assertThat(applicationPorts)
                        .describedAs("HTTP ports should be listed first")
                        .extracting(Port::getNumber)
                        .containsExactly(19000, 9090),
                () -> assertThat(adminPorts)
                        .extracting(Port::getNumber)
                        .containsExactlyInAnyOrder(19001)
            );
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getPorts(config, PortType.APPLICATION));
        }
    }

    @Nested
    class GetPort {

        @ParameterizedTest
        @CsvSource(textBlock = """
                HTTP, APPLICATION, 8900
                HTTP, ADMIN, 15000
                HTTPS, APPLICATION, 27500
                HTTPS, ADMIN, 10900
                """)
        void shouldFindExpectedPort(ConnectorType connectorType, PortType portType, int portNumber) {
            var applicationPortNumber = (portType == PortType.APPLICATION) ? portNumber : 8080;
            var adminPortNumber = (portType == PortType.ADMIN) ? portNumber : 8081;

            var serverFactory = newDefaultServerFactory(connectorType, applicationPortNumber, adminPortNumber);
            var config = newConfiguration(serverFactory);

            var port = DropwizardConnectors.getPort(config, portType, connectorType).orElseThrow();

            assertAll(
                () -> assertThat(port.getNumber()).isEqualTo(portNumber),
                () -> assertThat(port.getType()).isEqualTo(portType),
                () -> assertThat(port.getScheme()).isEqualTo(connectorType.scheme())
            );
        }

        @Test
        void shouldReturnEmptyOptionalWhenPortNotFound() {
            var serverFactory = newDefaultServerFactory(ConnectorType.HTTP, 9090, 9091);

            assertAll(
                () -> assertThat(DropwizardConnectors.getPort(serverFactory, PortType.APPLICATION, ConnectorType.HTTPS)).isEmpty(),
                () -> assertThat(DropwizardConnectors.getPort(serverFactory, PortType.ADMIN, ConnectorType.HTTPS)).isEmpty()
            );
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getPort(config, PortType.APPLICATION, ConnectorType.HTTPS));
        }
    }

    @Nested
    class GetApplicationPort {

        private DefaultServerFactory factory;

        @BeforeEach
        void setUp() {
            factory = new DefaultServerFactory();

            var connector = new HttpConnectorFactory();
            connector.setPort(8080);

            factory.setApplicationConnectors(List.of(connector));
        }

        @Test
        void shouldReturnApplicationPortWhenMatchIsFound() {
            var port = DropwizardConnectors.getApplicationPort(factory, ConnectorType.HTTP);
            assertThat(port).hasValue(8080);
        }

        @Test
        void shouldReturnEmptyWhenMatchIsNotFound() {
            var port = DropwizardConnectors.getApplicationPort(factory, ConnectorType.HTTPS);
            assertThat(port).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenConnectorsAreNotHttpConnectors() {
            var connector = new ConnectorFactory() {
                @Override
                public Connector build(Server server, MetricRegistry metricRegistry, String s, ThreadPool threadPool) {
                    return null;
                }
            };

            factory.setApplicationConnectors(List.of(connector));
            var port = DropwizardConnectors.getApplicationPort(factory, ConnectorType.HTTP);
            assertThat(port).isEmpty();
        }

        @Test
        void shouldSelectLastConnectorFactoryWhenGivenMoreThanOne() {
            var connectorFactories = buildHttpsConnectorFactories();
            factory.setApplicationConnectors(connectorFactories);

            var port = DropwizardConnectors.getApplicationPort(factory, ConnectorType.HTTPS);
            assertThat(port)
                    .describedAs("should always choose the port from the last ConnectorFactory")
                    .hasValue(8003);
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
                HTTP, 8900
                HTTPS, 10900
                """)
        void shouldAcceptConfiguration(ConnectorType connectorType, int applicationPortNumber) {
            var serverFactory = newDefaultServerFactory(connectorType, applicationPortNumber, 37000);
            var config = newConfiguration(serverFactory);

            var port = DropwizardConnectors.getApplicationPort(config, connectorType);
            assertThat(port).hasValue(applicationPortNumber);
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getApplicationPort(config, ConnectorType.HTTPS));
        }
    }

    @Nested
    class GetAdminPort {

        private DefaultServerFactory factory;

        @BeforeEach
        void setUp() {
            factory = new DefaultServerFactory();

            var connector = new HttpConnectorFactory();
            connector.setPort(8080);

            factory.setAdminConnectors(List.of(connector));
        }

        @Test
        void shouldReturnAdminPortWhenMatchIsFound() {
            var port = DropwizardConnectors.getAdminPort(factory, ConnectorType.HTTP);
            assertThat(port).hasValue(8080);
        }

        @Test
        void shouldReturnEmptyWhenMatchIsNotFound() {
            var port = DropwizardConnectors.getAdminPort(factory, ConnectorType.HTTPS);
            assertThat(port).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenConnectorsAreNotHttpConnectors() {
            var connector = new ConnectorFactory() {
                @Override
                public Connector build(Server server, MetricRegistry metricRegistry, String s, ThreadPool threadPool) {
                    return null;
                }
            };

            factory.setAdminConnectors(List.of(connector));
            var port = DropwizardConnectors.getAdminPort(factory, ConnectorType.HTTP);
            assertThat(port).isEmpty();
        }

        @Test
        void shouldSelectLastConnectorFactoryWhenGivenMoreThanOne() {
            var connectorFactories = buildHttpsConnectorFactories();
            factory.setAdminConnectors(connectorFactories);

            var port = DropwizardConnectors.getAdminPort(factory, ConnectorType.HTTPS);
            assertThat(port)
                    .describedAs("should always choose the port from the last ConnectorFactory")
                    .hasValue(8003);
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
                HTTP, 8900
                HTTPS, 10900
                """)
        void shouldAcceptConfiguration(ConnectorType connectorType, int adminPortNumber) {
            var serverFactory = newDefaultServerFactory(connectorType, 8000, adminPortNumber);
            var config = newConfiguration(serverFactory);

            var port = DropwizardConnectors.getAdminPort(config, connectorType);
            assertThat(port).hasValue(adminPortNumber);
        }

        @Test
        void shouldRequireConfigurationToHaveDefaultServerFactory() {
            var config = newConfigurationWithSimpleServerFactory();

            assertThatIllegalStateException()
                    .isThrownBy(() -> DropwizardConnectors.getAdminPort(config, ConnectorType.HTTPS));
        }
    }

    private static DefaultServerFactory newDefaultServerFactory(ConnectorType connectorType,
                                                                int applicationPortNumber,
                                                                int adminPortNumber) {

        var applicationConnectorFactory = newConnectorFactory(connectorType, applicationPortNumber);
        var adminConnectorFactory = newConnectorFactory(connectorType, adminPortNumber);

        var serverFactory = new DefaultServerFactory();
        serverFactory.setApplicationConnectors(List.of(applicationConnectorFactory));
        serverFactory.setAdminConnectors(List.of(adminConnectorFactory));

        return serverFactory;
    }

    private static HttpConnectorFactory newConnectorFactory(ConnectorType connectorType,
                                                            int portNumber) {
        var factory = newConnectorFactory(connectorType);
        factory.setPort(portNumber);
        return factory;
    }

    private static HttpConnectorFactory newConnectorFactory(ConnectorType connectorType) {
        return (connectorType == ConnectorType.HTTPS) ?
                new HttpsConnectorFactory() : new HttpConnectorFactory();
    }

    private static List<ConnectorFactory> buildHttpsConnectorFactories() {
        var httpsConnector1 = new HttpsConnectorFactory();
        httpsConnector1.setPort(8001);

        var httpsConnector2 = new HttpsConnectorFactory();
        httpsConnector2.setPort(8002);

        var httpsConnector3 = new HttpsConnectorFactory();
        httpsConnector3.setPort(8003);

        return List.of(httpsConnector1, httpsConnector2, httpsConnector3);
    }

    private static Configuration newConfigurationWithSimpleServerFactory() {
        return newConfiguration(new SimpleServerFactory());
    }

    private static Configuration newConfiguration(ServerFactory serverFactory) {
        var config = new Configuration();
        config.setServerFactory(serverFactory);
        return config;
    }

    @Nested
    class ConnectorTypeEnum {

        @Nested
        class ForHttpConnectorFactory {

            @Test
            void shouldThrow_GivenNullArgument() {
                //noinspection ResultOfMethodCallIgnored
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> ConnectorType.forHttpConnectorFactory(null))
                        .withMessage("factory cannot be null");
            }

            @ParameterizedTest
            @MethodSource("org.kiwiproject.dropwizard.util.server.DropwizardConnectorsTest#httpConnectorFactories")
            void shouldReturnHTTP_GivenHttpConnectorFactorySubclasses(HttpConnectorFactory factory) {
                assertThat(ConnectorType.forHttpConnectorFactory(factory)).isEqualTo(ConnectorType.HTTP);
            }

            @ParameterizedTest
            @MethodSource("org.kiwiproject.dropwizard.util.server.DropwizardConnectorsTest#httpsConnectorFactories")
            void shouldReturnHTTPS_GivenHttpsConnectorFactory(HttpConnectorFactory factory) {
                assertThat(ConnectorType.forHttpConnectorFactory(factory)).isEqualTo(ConnectorType.HTTPS);
            }
        }

        @ParameterizedTest
        @EnumSource(ConnectorType.class)
        void shouldReturnExpectedScheme(ConnectorType connectorType) {
            assertThat(connectorType.scheme())
                    .isEqualTo(connectorType.name().toLowerCase(Locale.ENGLISH));
        }
    }

    static Stream<HttpConnectorFactory> httpConnectorFactories() {
        return Stream.of(
                new HttpConnectorFactory(),
                new CustomHttpConnectorFactory()
        );
    }

    static Stream<HttpConnectorFactory> httpsConnectorFactories() {
        return Stream.of(
                new HttpsConnectorFactory(),
                new CustomHttpsConnectorFactory()
        );
    }

    static class CustomHttpConnectorFactory extends HttpConnectorFactory {
    }

    static class CustomHttpsConnectorFactory extends HttpsConnectorFactory {
    }

    @Nested
    class NewPort {

        @Test
        void shouldRequirePortNumber() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> DropwizardConnectors.newPort(PortType.APPLICATION, ConnectorType.HTTPS, null))
                    .withMessage("portNumber must not be null");
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
            APPLICATION, HTTP, 9900
            APPLICATION, HTTPS, 7500
            ADMIN, HTTP, 14500
            ADMIN, HTTPS, 29500
                """)
        void shouldCreateNewPort(PortType portType, ConnectorType connectorType, int portNumber) {
            var port = DropwizardConnectors.newPort(portType, connectorType, portNumber);

            var expectedSecure = (connectorType == ConnectorType.HTTPS) ?
                    Port.Security.SECURE : Port.Security.NOT_SECURE;

            assertAll(
                () -> assertThat(port.getType()).isEqualTo(portType),
                () -> assertThat(port.getSecure()).isEqualTo(expectedSecure),
                () -> assertThat(port.getNumber()).isEqualTo(portNumber)
            );
        }
    }
}
