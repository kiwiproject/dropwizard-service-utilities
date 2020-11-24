package org.kiwiproject.dropwizard.util.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
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
            var port = DropwizardConnectors.getApplicationPort(factory, DropwizardConnectors.ConnectorType.HTTP);
            assertThat(port).hasValue(8080);
        }

        @Test
        void shouldReturnEmptyWhenMatchIsNotFound() {
            var port = DropwizardConnectors.getApplicationPort(factory, DropwizardConnectors.ConnectorType.HTTPS);
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
            var port = DropwizardConnectors.getApplicationPort(factory, DropwizardConnectors.ConnectorType.HTTP);
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
            var port = DropwizardConnectors.getAdminPort(factory, DropwizardConnectors.ConnectorType.HTTP);
            assertThat(port).hasValue(8080);
        }

        @Test
        void shouldReturnEmptyWhenMatchIsNotFound() {
            var port = DropwizardConnectors.getAdminPort(factory, DropwizardConnectors.ConnectorType.HTTPS);
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
            var port = DropwizardConnectors.getAdminPort(factory, DropwizardConnectors.ConnectorType.HTTP);
            assertThat(port).isEmpty();
        }
    }

    @Nested
    class ConnectorType {

        @Test
        void forClass_ShouldThrowIllegalArgumentException_WhenTheClassDoesNotMatch() {
            class MyConnector implements ConnectorFactory {
                @Override
                public Connector build(Server server, MetricRegistry metricRegistry, String s, ThreadPool threadPool) {
                    return null;
                }
            }

            assertThatThrownBy(() -> DropwizardConnectors.ConnectorType.forClass(MyConnector.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unable to find ConnectorType for " + MyConnector.class.getName());
        }
    }
}
