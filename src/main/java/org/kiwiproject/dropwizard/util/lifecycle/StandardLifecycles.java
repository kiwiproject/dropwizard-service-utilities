package org.kiwiproject.dropwizard.util.lifecycle;

import static org.kiwiproject.base.KiwiStrings.format;

import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.config.ServiceInfo;
import org.kiwiproject.registry.management.RegistrationManager;
import org.kiwiproject.registry.management.dropwizard.RegistrationLifecycleListener;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.server.RegistryService;
import org.kiwiproject.registry.util.Ports;

import java.time.Instant;

/**
 * Utility to setup up some Dropwizard and Jetty lifecycle listeners
 */
@UtilityClass
@Slf4j
public class StandardLifecycles {

    /**
     * Logs the server status
     *
     * @param status the status of the server
     * @implNote This is logging at WARN level so that even in a production-like environment where logging levels may be set to WARN, the server
     * startup will always be logged.
     */
    public static void logServiceStatusWarningWithStatus(String status) {
        LOG.warn("==========================================================================");
        LOG.warn("SERVICE IS {}: {}", status, Instant.now());
        LOG.warn("==========================================================================");
    }

    /**
     * Adds lifecycle listeners to the Dropwizard environment that will register and unregister the service at startup and shutdown, respectively
     *
     * @param registryService   a pre-built {@link RegistryService} that will connect to a service discovery for registration/unregistration
     * @param serviceInfo       the metadata about the service (i.e. name, version, etc)
     * @param environment       the Dropwizard environment
     */
    public static void addRegistryLifecycleListeners(RegistryService registryService, ServiceInfo serviceInfo, Environment environment) {
        var registrationManager = new RegistrationManager(serviceInfo, registryService);

        var listener = new RegistrationLifecycleListener(registrationManager);

        // Handles registering at startup
        environment.lifecycle().addServerLifecycleListener(listener);

        // Handles unregistering at shutdown
        environment.lifecycle().addLifeCycleListener(listener);
    }

    public static void addServerConnectorLoggingLifecycleListener(Environment environment) {
        environment.lifecycle().addServerLifecycleListener(new ConnectorLoggingServerLifecycleListener());
    }

    /**
     * Adds a lifecycle listener that logs the current process id on startup.
     *
     * @param processId     the process id or null if unable to find it
     * @param environment   the Dropwizard environment
     */
    public static void addProcessIdLoggingLifecycleListener(Integer processId, Environment environment) {
        environment.lifecycle().addServerLifecycleListener(new ProcessIdLoggingServerLifecycleListener(processId));
    }

    /**
     * Adds a lifecycle listener to print out the status of the server with configured ports at startup.
     *
     * @param serviceInfo   the metadata about the service (i.e. name, version, etc)
     * @param environment   the Dropwizard environment
     */
    public static void addServiceRunningLifecycleListener(ServiceInfo serviceInfo, Environment environment) {
        ServerLifecycleListener listener = server -> {
            var appPort = Ports.findFirstPortPreferSecure(serviceInfo.getPorts(), Port.PortType.APPLICATION);
            var adminPort = Ports.findFirstPortPreferSecure(serviceInfo.getPorts(), Port.PortType.ADMIN);
            var status = format("RUNNING (port: {}/{}, admin port: {}/{})",
                    appPort.getNumber(), appPort.getSecure().getScheme(), adminPort.getNumber(), adminPort.getSecure().getScheme());
            logServiceStatusWarningWithStatus(status);
        };

        environment.lifecycle().addServerLifecycleListener(listener);
    }
}
