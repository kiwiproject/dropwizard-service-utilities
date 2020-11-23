package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.collect.KiwiLists.first;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.config.provider.TlsConfigProvider;
import org.kiwiproject.net.LocalPortChecker;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

@Slf4j
public class PortAssigner<T extends Configuration> {
    private final LocalPortChecker localPortChecker;
    private final TlsConfigProvider tlsProvider;

    PortAssigner(TlsConfigProvider tlsConfigProvider) {
        this(new LocalPortChecker(), tlsConfigProvider);
    }

    @VisibleForTesting
    PortAssigner(LocalPortChecker portChecker, TlsConfigProvider t1sPropertyProvider) {
        this.localPortChecker = portChecker;
        this.tlsProvider = t1sPropertyProvider;
    }

    void assignDynamicPorts(T config) {
        if (!config.isUseDynamicPorts()) {
            return;
        }

        var portRange = new PortRangeInfo(config);
        var usedPorts = new HashSet<Integer>();

        DefaultServerFactory serverFactory = getServerFactory(config);
        if (config.isUseSecureDynamicPorts()) {
            assignSecureDynamicPorts(portRange, usedPorts, serverFactory);
        } else {
            assignNonSecureDynamicPorts(portRange, usedPorts, serverFactory);
        }
    }

    private DefaultServerFactory getServerFactory(T configuration) {
        return DropwizardConnectors.getDefaultServerFactory(configuration.getServerFactory());
    }

    private void assignSecureDynamicPorts(PortRangeInfo portRange, Set<Integer> usedPorts, DefaultServerFactory serverFactory) {
        LOG.debug("Secure (https): replace Dropwizard HTTP app/admin connectors with HTTPS ones using dynamic ports");

        assertCoconutCanProvideTlsProperties();

        var tlsConfig = tlsProvider.getTlsContextConfiguration();

        int appPort = findFreePort(portRange, usedPorts);
        var secureApp = newHttpsConnectorFactory(appPort, tlsConfig);

        serverFactory.setApplicationConnectors(List.of(secureApp));

        int adminPort = findFreePort(portRange, usedPorts);
        var secureAdmin = newHttpsConnectorFactory(adminPort, tlsConfig);

        serverFactory.setAdminConnectors(List.of(secureAdmin));
    }

    private void assertCoconutCanProvideTlsProperties() {
        if (tlsProvider.canNotProvide()) {
            throw new IllegalStateException("TlsConfigProvider cannot provide TLS properties, so cannot assign keystore!");
        }
    }

    private static HttpsConnectorFactory newHttpsConnectorFactory(int port, TlsContextConfiguration tlsConfig) {
        var https = new HttpsConnectorFactory();

        https.setPort(port);
        https.setKeyStorePath(tlsConfig.getKeyStorePath());
        https.setKeyStorePassword(tlsConfig.getKeyStorePassword());
        https.setTrustStorePath(tlsConfig.getTrustStorePath());
        https.setTrustStorePassword(tlsConfig.getTrustStorePassword());
        https.setSupportedProtocols(tlsConfig.getSupportedProtocols());

        return https;
    }

    private void assignNonSecureDynamicPorts(PortRangeInfo portRange, Set<Integer> usedPorts, DefaultServerFactory serverFactory) {
        LOG.debug("Insecure (http): modify Dropwizard HTTP app/admin connectors using dynamic ports");

        int connectorPort = findFreePort(portRange, usedPorts);
        var app = (HttpConnectorFactory) first(serverFactory.getApplicationConnectors());
        app.setPort(connectorPort);

        int adminPort = findFreePort(portRange, usedPorts);
        var admin = (HttpConnectorFactory) first(serverFactory.getAdminConnectors());
        admin.setPort(adminPort);

        LOG.warn("This server has been explicitly configured to run with dynamically assigned ports in NON-SECURE mode (HTTP)!");
    }

    /**
     * @implNote Mutates {@code usedPorts} for each used port it finds
     */
    private int findFreePort(PortRangeInfo portRange, Set<Integer> usedPorts) {
        IntSupplier portSupplier = () -> portRange.minPortNumber + ThreadLocalRandom.current().nextInt(portRange.numPortsInRange);
        OptionalInt assignedPort = IntStream.generate(portSupplier)
                .limit(portRange.maxPortCheckAttempts)
                .filter(port -> availableAndUnused(port, usedPorts))
                .findFirst();

        if (assignedPort.isPresent()) {
            usedPorts.add(assignedPort.getAsInt());
            return assignedPort.getAsInt();
        }

        throw new NoAvailablePortException(format("Could not find an available port between %s and %s after %s attempts. I give up.",
                portRange.minPortNumber, portRange.maxPortNumber, portRange.maxPortCheckAttempts));
    }

    private boolean availableAndUnused(int port, Set<Integer> usedPorts) {
        LOG.trace("Checking if port {} is available", port);
        return localPortChecker.isPortAvailable(port) && !usedPorts.contains(port);
    }
}
