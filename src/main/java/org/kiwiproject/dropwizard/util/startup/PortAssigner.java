package org.kiwiproject.dropwizard.util.startup;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.dropwizard.util.server.DropwizardConnectors.requireDefaultServerFactory;

import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

/**
 * Finds open ports and sets up the application and admin connectors with those ports. There are a couple of opinionated decisions here:
 * <ul>
 *     <li>{@link TlsConfigProvider} is required if secure connectors are wanted</li>
 *     <li>The default {@link PortAssignment} is {@code DYNAMIC}</li>
 *     <li>If {@code allowablePortRange} is null, then a zero will be passed to the connector leaving the dynamic port up to the server</li>
 *     <li>The default {@link PortSecurity} is {@code SECURE}, because we should all be more secure</li>
 * </ul>
 */
@Slf4j
@Getter(AccessLevel.PACKAGE) // For testing
public class PortAssigner {

    public enum PortAssignment {
        STATIC, DYNAMIC
    }

    public enum PortSecurity {
        SECURE, NON_SECURE
    }

    private final LocalPortChecker localPortChecker;
    private final TlsContextConfiguration tlsConfiguration;
    private final PortAssignment portAssignment;
    private final AllowablePortRange allowablePortRange;
    private final DefaultServerFactory serverFactory;
    private final PortSecurity portSecurity;

    @Builder
    private PortAssigner(LocalPortChecker localPortChecker,
                         TlsContextConfiguration tlsConfiguration,
                         PortAssignment portAssignment,
                         AllowablePortRange allowablePortRange,
                         ServerFactory serverFactory,
                         PortSecurity portSecurity) {

        this.localPortChecker = Optional.ofNullable(localPortChecker).orElse(new LocalPortChecker());
        this.portSecurity = Optional.ofNullable(portSecurity).orElse(PortSecurity.SECURE);
        this.tlsConfiguration = (this.portSecurity == PortSecurity.SECURE) ? requireNotNull(tlsConfiguration) : null;
        this.portAssignment = Optional.ofNullable(portAssignment).orElse(PortAssignment.DYNAMIC);
        this.allowablePortRange = allowablePortRange;
        this.serverFactory = requireDefaultServerFactory(serverFactory);
    }

    /**
     * Sets up the connectors with a dynamic available port if {@link PortAssigner} is configured for dynamic ports.
     */
    public void assignDynamicPorts() {
        if (portAssignment == PortAssignment.STATIC) {
            LOG.info("Static port assignment is being used, will rely on Dropwizard configuration for the connector setup");
            return;
        }

        if (portSecurity == PortSecurity.SECURE) {
            assignSecureDynamicPorts();
        } else {
            assignNonSecureDynamicPorts();
        }
    }

    private void assignSecureDynamicPorts() {
        LOG.debug("Secure (https): replace Dropwizard HTTP app/admin connectors with HTTPS ones using dynamic ports");

        var usedPorts = new HashSet<Integer>();

        int appPort = findFreePort(usedPorts);
        var secureApp = newHttpsConnectorFactory(appPort);

        serverFactory.setApplicationConnectors(List.of(secureApp));

        int adminPort = findFreePort(usedPorts);
        var secureAdmin = newHttpsConnectorFactory(adminPort);

        serverFactory.setAdminConnectors(List.of(secureAdmin));
    }

    private HttpsConnectorFactory newHttpsConnectorFactory(int port) {
        var https = new HttpsConnectorFactory();

        https.setPort(port);
        https.setKeyStorePath(tlsConfiguration.getKeyStorePath());
        https.setKeyStorePassword(tlsConfiguration.getKeyStorePassword());
        https.setTrustStorePath(tlsConfiguration.getTrustStorePath());
        https.setTrustStorePassword(tlsConfiguration.getTrustStorePassword());
        https.setSupportedProtocols(tlsConfiguration.getSupportedProtocols());

        return https;
    }

    private void assignNonSecureDynamicPorts() {
        LOG.debug("Insecure (http): modify Dropwizard HTTP app/admin connectors using dynamic ports");

        var usedPorts = new HashSet<Integer>();

        int applicationPort = findFreePort(usedPorts);
        var app = (HttpConnectorFactory) first(serverFactory.getApplicationConnectors());
        app.setPort(applicationPort);

        int adminPort = findFreePort(usedPorts);
        var admin = (HttpConnectorFactory) first(serverFactory.getAdminConnectors());
        admin.setPort(adminPort);

        LOG.warn("This server has been explicitly configured to run with dynamically assigned ports in NON-SECURE mode (HTTP)!");
    }

    /**
     * @implNote Mutates {@code usedPorts} for each used port it finds
     */
    private int findFreePort(Set<Integer> usedPorts) {
        if (isNull(allowablePortRange)) {
            return 0;
        }

        IntSupplier portSupplier = () -> allowablePortRange.minPortNumber + ThreadLocalRandom.current().nextInt(allowablePortRange.numPortsInRange);
        var assignedPort = IntStream.generate(portSupplier)
                .limit(allowablePortRange.maxPortCheckAttempts)
                .filter(port -> availableAndUnused(port, usedPorts))
                .findFirst();

        if (assignedPort.isPresent()) {
            usedPorts.add(assignedPort.getAsInt());
            return assignedPort.getAsInt();
        }

        throw new NoAvailablePortException(format("Could not find an available port between {} and {} after {} attempts. I give up.",
                allowablePortRange.minPortNumber, allowablePortRange.maxPortNumber, allowablePortRange.maxPortCheckAttempts));
    }

    private boolean availableAndUnused(int port, Set<Integer> usedPorts) {
        LOG.trace("Checking if port {} is available", port);
        return localPortChecker.isPortAvailable(port) && !usedPorts.contains(port);
    }
}
