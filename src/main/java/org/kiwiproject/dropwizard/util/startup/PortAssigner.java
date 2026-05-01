package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.dropwizard.util.server.DropwizardConnectors.requireDefaultServerFactory;

import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.List;
import java.util.Optional;

/**
 * Finds open ports and sets up the application and admin connectors with those ports. There are a couple of opinionated decisions here:
 * <ul>
 *     <li>{@link TlsContextConfiguration} is required if secure connectors are wanted</li>
 *     <li>The default {@link PortAssignment} is {@code DYNAMIC}</li>
 *     <li>
 *         The default {@link FreePortFinder} is a random port finder. If {@code allowablePortRange} is null,
 *         then a zero will be passed to the connector leaving the dynamic port up to the server.
 *         See {@link RandomFreePortFinder}.
 *     </li>
 *     <li>The default {@link PortSecurity} is {@code SECURE}, because we should all be more secure</li>
 *     <li>{@link ServerFactory} is required, and must be an instance of {@link DefaultServerFactory}</li>
 *     <li>
 *         This class will set up at MOST one application port and one admin port.
 *         Currently, you can NOT use this and have both secure and non-secure ports.
 *     </li>
 * </ul>
 * The reason a {@link ServerFactory} is the type accepted in the builder is that Dropwizard's {@code Configuration}
 * class returns the interface type. So, even though a {@link DefaultServerFactory} is required, client
 * code can get the {@code ServerFactory} from the {@code Configuration} and use it directly in this class without
 * needing an explicit type cast.
 */
@Slf4j
@Getter(AccessLevel.PACKAGE) // For testing
public class PortAssigner {

    /**
     * An enum that represents either static or dynamic port assignment.
     */
    public enum PortAssignment {
        STATIC, DYNAMIC;

        /**
         * Return {@link #DYNAMIC} when the given value is true, otherwise {@link #STATIC}.
         *
         * @param value the boolean value to convert
         * @return the equivalent {@link PortAssignment} instance
         */
        public static PortAssignment fromBooleanDynamicWhenTrue(boolean value) {
            return value ? DYNAMIC : STATIC;
        }
    }

    /**
     * An enum that represents whether a port is secure (HTTPS) or not secure (HTTP).
     */
    public enum PortSecurity {
        SECURE, NON_SECURE;

        /**
         * Return {@link #SECURE} when the given value is true, otherwise {@link #NON_SECURE}.
         *
         * @param value the boolean value to convert
         * @return the equivalent {@link PortSecurity} instance
         */
        public static PortSecurity fromBooleanSecureWhenTrue(boolean value) {
            return value ? SECURE : NON_SECURE;
        }

        /**
         * Return the instance of this enum which is equivalent to the given {@link Port.Security}.
         *
         * @param security the {@link Port.Security} value to convert
         * @return the equivalent {@link PortSecurity} instance
         */
        public static PortSecurity fromSecurity(Port.Security security) {
            checkArgumentNotNull(security, "security must not be null");
            return (security == Port.Security.SECURE) ? SECURE : NON_SECURE;
        }

        /**
         * Convert this instance to a {@link Port.Security} instance.
         *
         * @return the equivalent {@link Port.Security} instance
         */
        public Port.Security toSecurity() {
            return (this == PortSecurity.SECURE) ? Port.Security.SECURE : Port.Security.NOT_SECURE;
        }
    }

    private final TlsContextConfiguration tlsConfiguration;
    private final PortAssignment portAssignment;
    private final FreePortFinder freePortFinder;
    private final AllowablePortRange allowablePortRange;
    private final DefaultServerFactory serverFactory;
    private final PortSecurity portSecurity;

    @Builder
    private PortAssigner(@Nullable TlsContextConfiguration tlsConfiguration,
                         @Nullable PortAssignment portAssignment,
                         @Nullable FreePortFinder freePortFinder,
                         @Nullable AllowablePortRange allowablePortRange,
                         ServerFactory serverFactory,
                         @Nullable PortSecurity portSecurity) {

        this.portSecurity = Optional.ofNullable(portSecurity).orElse(PortSecurity.SECURE);
        this.tlsConfiguration = (this.portSecurity == PortSecurity.SECURE) ?
                requireNotNull(tlsConfiguration, "tlsConfiguration must not be null when using secure ports") : null;
        this.portAssignment = Optional.ofNullable(portAssignment).orElse(PortAssignment.DYNAMIC);
        this.freePortFinder = Optional.ofNullable(freePortFinder).orElseGet(RandomFreePortFinder::new);
        this.allowablePortRange = allowablePortRange;
        this.serverFactory = requireDefaultServerFactory(serverFactory);
    }

    /**
     * Sets up the application and admin connectors with a dynamic available port
     * if {@link PortAssigner} is configured for dynamic ports.
     * <p>
     * When using {@link PortSecurity#NON_SECURE HTTP},
     * only the <em>first</em> application and admin connectors have dynamic ports assigned.
     * If an application is using multiple connectors, it's best to avoid using this class
     * unless you only want the first ones dynamically assigned.
     * <p>
     * When using {@link PortSecurity#SECURE HTTPS}, the behavior depends on whether explicit
     * HTTPS connectors are already configured in the server factory:
     * <ul>
     *     <li>
     *         If the first application <em>and</em> admin connectors are already
     *         {@link HttpsConnectorFactory} instances (e.g., configured in YAML as
     *         {@code type: https}), dynamic ports are assigned and TLS properties from the
     *         {@link TlsContextConfiguration} are overlaid onto the existing connectors.
     *         All other connector properties (e.g., {@code idleTimeout}, {@code outputBufferSize})
     *         are preserved from the existing YAML configuration.
     *     </li>
     *     <li>
     *         Otherwise, new {@link HttpsConnectorFactory} instances are created from scratch
     *         using the {@link TlsContextConfiguration}, replacing any existing connectors.
     *         This is the default behavior when no explicit server configuration is defined in YAML.
     *     </li>
     * </ul>
     *
     * @return the list of ports in the ServerFactory after assigning dynamic ports
     * @implNote Both the application and admin connectors must be {@link HttpsConnectorFactory}
     * instances for the overlay path to be taken. If only one is {@code https}, we create new
     * connectors rather than a partial overlay. We are in {@link PortSecurity#SECURE} mode, and
     * a partial overlay would produce inconsistent behavior; one connector instance preserved
     * from YAML while the other is created from scratch.
     */
    public List<Port> assignDynamicPorts() {
        if (portAssignment == PortAssignment.STATIC) {
            LOG.info("Static port assignment is being used, will rely on Dropwizard configuration for the connector setup");
            return DropwizardConnectors.getPorts(serverFactory);
        }

        if (portSecurity == PortSecurity.SECURE) {
            return assignSecureDynamicPorts();
        } else {
            return assignDynamicPortsToExistingConnectors();
        }
    }

    private List<Port> assignSecureDynamicPorts() {
        if (first(serverFactory.getApplicationConnectors()) instanceof HttpsConnectorFactory appConnector
                && first(serverFactory.getAdminConnectors()) instanceof HttpsConnectorFactory adminConnector) {
            LOG.debug("Found existing HTTPS app/admin connectors; overlaying TLS config and assigning dynamic ports");
            return overlaySecureDynamicPortsOnExistingConnectors(appConnector, adminConnector);
        }
        return assignSecureDynamicPortsToNewConnectors();
    }

    private List<Port> overlaySecureDynamicPortsOnExistingConnectors(HttpsConnectorFactory appConnector,
                                                                      HttpsConnectorFactory adminConnector) {
        var servicePorts = freePortFinder.find(allowablePortRange);

        var appPort = servicePorts.applicationPort();
        appConnector.setPort(appPort);
        applyTlsConfiguration(appConnector, tlsConfiguration);

        var adminPort = servicePorts.adminPort();
        adminConnector.setPort(adminPort);
        applyTlsConfiguration(adminConnector, tlsConfiguration);

        LOG.info("Assigned application port as {} and admin port as {} to existing HTTPS connectors",
                appPort, adminPort);

        return List.of(
            Port.of(appPort, PortType.APPLICATION, Port.Security.SECURE),
            Port.of(adminPort, PortType.ADMIN, Port.Security.SECURE)
        );
    }

    private List<Port> assignSecureDynamicPortsToNewConnectors() {
        LOG.debug("Replace Dropwizard app/admin connectors with HTTPS ones using dynamic ports");

        var servicePorts = freePortFinder.find(allowablePortRange);

        var appPort = servicePorts.applicationPort();
        var secureAppFactory = newHttpsConnectorFactory(appPort);
        serverFactory.setApplicationConnectors(List.of(secureAppFactory));

        var adminPort = servicePorts.adminPort();
        var secureAdminFactory = newHttpsConnectorFactory(adminPort);
        serverFactory.setAdminConnectors(List.of(secureAdminFactory));

        return List.of(
            Port.of(appPort, PortType.APPLICATION, Port.Security.SECURE),
            Port.of(adminPort, PortType.ADMIN, Port.Security.SECURE)
        );
    }

    private HttpsConnectorFactory newHttpsConnectorFactory(int port) {
        var https = new HttpsConnectorFactory();
        https.setPort(port);
        applyTlsConfiguration(https, tlsConfiguration);
        return https;
    }

    private static void applyTlsConfiguration(HttpsConnectorFactory https, TlsContextConfiguration tls) {
        https.setKeyStorePath(tls.getKeyStorePath());
        https.setKeyStorePassword(tls.getKeyStorePassword());
        https.setKeyStoreType(tls.getKeyStoreType());
        https.setKeyStoreProvider(tls.getKeyStoreProvider());
        https.setTrustStorePath(tls.getTrustStorePath());
        https.setTrustStorePassword(tls.getTrustStorePassword());
        https.setTrustStoreType(tls.getTrustStoreType());
        https.setTrustStoreProvider(tls.getTrustStoreProvider());
        https.setJceProvider(tls.getProvider());
        https.setCertAlias(tls.getCertAlias());
        https.setSupportedProtocols(tls.getSupportedProtocols());
        https.setSupportedCipherSuites(tls.getSupportedCiphers());
        https.setDisableSniHostCheck(tls.isDisableSniHostCheck());
    }

    private List<Port> assignDynamicPortsToExistingConnectors() {
        LOG.debug("Modify existing Dropwizard HTTP(S) app/admin connectors using dynamic ports");

        var servicePorts = freePortFinder.find(allowablePortRange);

        var applicationPort = servicePorts.applicationPort();
        var appFactory = (HttpConnectorFactory) first(serverFactory.getApplicationConnectors());
        appFactory.setPort(applicationPort);

        var adminPort = servicePorts.adminPort();
        var adminFactory = (HttpConnectorFactory) first(serverFactory.getAdminConnectors());
        adminFactory.setPort(adminPort);

        LOG.info("Assigned application port as {} and admin port as {} to existing connectors",
                applicationPort, adminPort);

        return List.of(
            Port.of(applicationPort, PortType.APPLICATION, portSecurityOf(appFactory)),
            Port.of(adminPort, PortType.ADMIN, portSecurityOf(adminFactory))
        );
    }

    private static Port.Security portSecurityOf(ConnectorFactory connectorFactory) {
        if (connectorFactory instanceof HttpsConnectorFactory) {
            return Port.Security.SECURE;
        }

        return Port.Security.NOT_SECURE;
    }

}
