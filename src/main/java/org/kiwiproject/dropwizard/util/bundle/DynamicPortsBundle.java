package org.kiwiproject.dropwizard.util.bundle;

import static org.kiwiproject.dropwizard.util.bundle.PortAssigners.allowablePortRangeFrom;
import static org.kiwiproject.dropwizard.util.bundle.PortAssigners.portAssignmentFrom;
import static org.kiwiproject.dropwizard.util.bundle.PortAssigners.portSecurityFrom;
import static org.kiwiproject.dropwizard.util.server.DropwizardConnectors.getAdminPorts;
import static org.kiwiproject.dropwizard.util.server.DropwizardConnectors.getApplicationPorts;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;

/**
 * Dropwizard bundle that assigns ports dynamically during application initialization.
 * See {@link DynamicPortsConfiguration} for the various configuration options.
 * <p>
 * Port assignment occurs before the Dropwizard application starts the Jetty server.
 * <p>
 * This bundle should be registered before any other bundles that use ports, for example, the
 * {@code ConsulBundle} in <a href="https://github.com/kiwiproject/dropwizard-consul">dropwizard-consul</a>.
 * This ensures downstream bundles obtain the correct ports to use when registering with
 * a service registry, such as <a href="https://www.hashicorp.com/products/consul">Consul</a>.
 */
@Slf4j
public abstract class DynamicPortsBundle<C extends Configuration>
        implements ConfiguredBundle<C>, DynamicPortsConfigured<C> {

    @Override
    public void run(C configuration, Environment environment) {
        LOG.trace("Running DynamicPortsBundle");

        var dynamicPortsConfig = getDynamicPortsConfiguration(configuration);

        var portAssignment = portAssignmentFrom(dynamicPortsConfig);
        var portRange = allowablePortRangeFrom(dynamicPortsConfig);
        var portSecurity = portSecurityFrom(dynamicPortsConfig);

        var portAssigner = PortAssigner.builder()
                .portAssignment(portAssignment)
                .allowablePortRange(portRange)
                .portSecurity(portSecurity)
                .serverFactory(configuration.getServerFactory())
                .tlsConfiguration(dynamicPortsConfig.getTlsContextConfiguration())
                .freePortFinder(dynamicPortsConfig.getFreePortFinder())
                .build();

        portAssigner.assignDynamicPorts();

        LOG.info("Dynamic ports? {} ; application port(s): {} / admin port(s): {}",
                dynamicPortsConfig.isUseDynamicPorts(),
                getApplicationPorts(configuration),
                getAdminPorts(configuration));
    }
}
