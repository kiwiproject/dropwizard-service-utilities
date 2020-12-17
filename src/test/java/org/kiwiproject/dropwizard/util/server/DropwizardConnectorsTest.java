package org.kiwiproject.dropwizard.util.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.dropwizard.server.SimpleServerFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors.ConnectorType;

import java.util.List;

@DisplayName("DropwizardConnectors")
class DropwizardConnectorsTest {

    @Nested
    class GetDefaultServerFactory {
        @Test
        void throwsIllegalStateException_WhenServerFactory_IsNotInstance0fDefaultServerFactory() {
            ServerFactory factory = new SimpleServerFactory();
            assertThatThrownBy(() -> DropwizardConnectors.requireDefaultServerFactory(factory))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("The server factory is not a io.dropwizard.server.DefaultServerFactory (it is a ")
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
            @ValueSource(classes = {HttpConnectorFactory.class, CustomHttpConnectorFactory.class})
            void shouldReturnHTTP_GivenHttpConnectorFactorySubclasses(Class<? extends HttpConnectorFactory> type) {
                var factory = mock(type);
                assertThat(ConnectorType.forHttpConnectorFactory(factory)).isEqualTo(ConnectorType.HTTP);
            }

            @ParameterizedTest
            @ValueSource(classes = {HttpsConnectorFactory.class, CustomHttpsConnectorFactory.class})
            void shouldReturnHTTPS_GivenHttpsConnectorFactory(Class<? extends HttpConnectorFactory> type) {
                var factory = mock(type);
                assertThat(ConnectorType.forHttpConnectorFactory(factory)).isEqualTo(ConnectorType.HTTPS);
            }
        }
    }

    static class CustomHttpConnectorFactory extends HttpConnectorFactory {
    }

    static class CustomHttpsConnectorFactory extends HttpsConnectorFactory {
    }

}
