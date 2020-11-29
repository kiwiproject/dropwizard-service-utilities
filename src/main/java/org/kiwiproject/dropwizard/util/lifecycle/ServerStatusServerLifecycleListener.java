package org.kiwiproject.dropwizard.util.lifecycle;

import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.dropwizard.util.lifecycle.StandardLifecycles.logServiceStatusWarningWithStatus;

import io.dropwizard.lifecycle.ServerLifecycleListener;
import org.eclipse.jetty.server.Server;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.util.Ports;

class ServerStatusServerLifecycleListener implements ServerLifecycleListener {

    private final ServiceInfo serviceInfo;

    public ServerStatusServerLifecycleListener(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    @Override
    public void serverStarted(Server server) {
        var ports = serviceInfo.getPorts();
        var appPort = Ports.findFirstPortPreferSecure(ports, Port.PortType.APPLICATION);
        var adminPort = Ports.findFirstPortPreferSecure(ports, Port.PortType.ADMIN);

        var status = format("RUNNING (port: {}/{}, admin port: {}/{})",
                appPort.getNumber(), appPort.getSecure().getScheme(), adminPort.getNumber(),
                adminPort.getSecure().getScheme());

        logServiceStatusWarningWithStatus(status);
    }
}
