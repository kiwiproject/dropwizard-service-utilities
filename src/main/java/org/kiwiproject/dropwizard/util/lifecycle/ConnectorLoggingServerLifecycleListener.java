package org.kiwiproject.dropwizard.util.lifecycle;

import io.dropwizard.lifecycle.ServerLifecycleListener;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.Arrays;

@Slf4j
public class ConnectorLoggingServerLifecycleListener implements ServerLifecycleListener {

    @Override
    public void serverStarted(Server server) {
        Arrays.stream(server.getConnectors())
                .filter(connector -> connector instanceof ServerConnector)
                .forEach(connector ->
                        LOG.info("Server connector [{}] is on port [{}]", connector.getName(), getLocalPort(connector)));
    }

    private static int getLocalPort(Connector connector) {
        return ((ServerConnector) connector).getLocalPort();
    }

}
