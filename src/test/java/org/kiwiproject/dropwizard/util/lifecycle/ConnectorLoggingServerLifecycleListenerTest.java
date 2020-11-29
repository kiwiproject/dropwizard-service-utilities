package org.kiwiproject.dropwizard.util.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectorLoggingServerLifecycleListener")
class ConnectorLoggingServerLifecycleListenerTest {

    @Test
    void shouldNotThrowException() {
        var listener = new ConnectorLoggingServerLifecycleListener();
        var server = new Server(8080);

        assertThatCode(() -> listener.serverStarted(server)).doesNotThrowAnyException();
    }
}
