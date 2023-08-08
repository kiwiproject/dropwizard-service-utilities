package org.kiwiproject.dropwizard.util.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.MetricRegistry;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors.ConnectorType;

import java.util.List;
import java.util.stream.Stream;

@DisplayName("DropwizardConnectors")
class DropwizardConnectorsTest {

    @Nested
    class GetDefaultServerFactory {
        @Test
        void throwsIllegalStateException_WhenServerFactory_IsNotInstanceOfDefaultServerFactory() {
            ServerFactory factory = new SimpleServerFactory();
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory(factory))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("The server factory is not a %s (it is a ", DefaultServerFactory.class.getName())
                    .hasMessageEndingWith("SimpleServerFactory)");
        }

        @Test
        void throwsIllegalArgumentException_WhenServerFactory_IsNull() {
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory(null))
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

}
