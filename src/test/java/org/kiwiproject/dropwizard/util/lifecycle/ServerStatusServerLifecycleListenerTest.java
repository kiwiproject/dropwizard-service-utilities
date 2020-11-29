package org.kiwiproject.dropwizard.util.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port;

import java.util.List;

@DisplayName("ServerStatusServerLifecycleListener")
class ServerStatusServerLifecycleListenerTest {

    @Test
    void shouldNotThrowException() {
        var serviceInfo = mock(ServiceInfo.class);

        var ports = List.of(
                Port.of(8080, Port.PortType.APPLICATION, Port.Security.SECURE),
                Port.of(8081, Port.PortType.ADMIN, Port.Security.SECURE)
        );

        when(serviceInfo.getPorts()).thenReturn(ports);

        var listener = new ServerStatusServerLifecycleListener(serviceInfo);
        var server = new Server(8080);

        assertThatCode(() -> listener.serverStarted(server)).doesNotThrowAnyException();
    }
}
