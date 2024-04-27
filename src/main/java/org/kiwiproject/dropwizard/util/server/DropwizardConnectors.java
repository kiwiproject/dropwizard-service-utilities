package org.kiwiproject.dropwizard.util.server;

import static com.google.common.base.Preconditions.checkState;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.collect.KiwiLists.first;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility class that assists with setting up the server connectors in Dropwizard.
 */
@UtilityClass
@Slf4j
public class DropwizardConnectors {

    /**
     * Enum defining the possible options for a connector type in Dropwizard.
     */
    public enum ConnectorType {
        HTTP("http"), HTTPS("https");

        /**
         * The scheme (in a URL) for this type of connector.
         */
        @Getter
        @Accessors(fluent = true)
        final String scheme;

        ConnectorType(String scheme) {
            this.scheme = scheme;
        }

        /**
         * Given an {@link HttpConnectorFactory} instance, determine whether it is for HTTP or HTTPS.
         *
         * @param factory the instance
         * @return the ConnectorType
         * @implNote Assumes there is only {@link HttpConnectorFactory} and one
         * subclass, {@link HttpsConnectorFactory}. This is what has existed in Dropwizard for many
         * years now. HTTPS is returned for {@link HttpsConnectorFactory}, and HTTP for anything else.
         */
        static ConnectorType forHttpConnectorFactory(HttpConnectorFactory factory) {
            checkArgumentNotNull(factory, "factory cannot be null");

            if (factory instanceof HttpsConnectorFactory) {
                return HTTPS;
            }

            return HTTP;
        }
    }

    /**
     * Requires that the given {@link Configuration} contains a {@link ServerFactory} that is
     * a {@link DefaultServerFactory}.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return the server factory from the configuration if it is an instance of {@link DefaultServerFactory}
     * @throws IllegalStateException    if serverFactory is not a {@link DefaultServerFactory}
     */
    public static <C extends Configuration> DefaultServerFactory requireDefaultServerFactory(C configuration) {
        checkArgumentNotNull(configuration, "configuration is required");

        return requireDefaultServerFactory(configuration.getServerFactory());
    }

    /**
     * Requires that the given {@link ServerFactory} is in fact a {@link DefaultServerFactory}.
     *
     * @param serverFactory {@link ServerFactory} to check to make sure it is a {@link DefaultServerFactory}
     * @return the given server factory if it is an instance of {@link DefaultServerFactory}
     * @throws IllegalStateException    if serverFactory is not a {@link DefaultServerFactory}
     * @throws IllegalArgumentException if serverFactory is null
     */
    public static DefaultServerFactory requireDefaultServerFactory(ServerFactory serverFactory) {
        checkArgumentNotNull(serverFactory, "ServerFactory is required");

        if (serverFactory instanceof DefaultServerFactory defaultServerFactory) {
            return defaultServerFactory;
        }

        var error = format("The server factory is not a {} (it is a {})",
                DefaultServerFactory.class.getName(), serverFactory.getClass().getName());
        throw new IllegalStateException(error);
    }

    /**
     * Get all the ports.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return a list of all the ports in the Dropwizard configuration
     */
    public static <C extends Configuration> List<Port> getPorts(C configuration) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getPorts(serverFactory);
    }

    /**
     * Get all the ports.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the ports from
     * @return a list of all the ports in the server factory
     */
    public static List<Port> getPorts(DefaultServerFactory serverFactory) {
        return Stream.of(getApplicationPorts(serverFactory), getAdminPorts(serverFactory))
                .flatMap(Collection::stream)
                .toList();
    }

    /**
     * Get the single application port. If there is not exactly one, throw an exception.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return the single application port
     * @throws IllegalStateException if there is not exactly one application port
     */
    public static <C extends Configuration> Port getOnlyApplicationPort(C configuration) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getOnlyApplicationPort(serverFactory);
    }

    /**
     *  Get the single application port. If there is not exactly one, throw an exception.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the single application port from
     * @return the application port
     * @throws IllegalStateException if there is more than one port
     */
    public static Port getOnlyApplicationPort(DefaultServerFactory serverFactory) {
        var applicationPorts = getApplicationPorts(serverFactory);
        checkExactlyOnePort("application", applicationPorts);
        return first(applicationPorts);
    }

    /**
     * Find only the application ports.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return the application ports
     */
    public static <C extends Configuration> List<Port> getApplicationPorts(C configuration) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getApplicationPorts(serverFactory);
    }

    /**
     * Find only the application ports.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the application ports from
     * @return the application ports
     */
    public static List<Port> getApplicationPorts(DefaultServerFactory serverFactory) {
        return getPorts(serverFactory, PortType.APPLICATION);
    }

    /**
     * Get the single admin port. If there is not exactly one, throw an exception.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return the admin port
     * @throws IllegalStateException if there is not exactly one admin port
     */
    public static <C extends Configuration> Port getOnlyAdminPort(C configuration) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getOnlyAdminPort(serverFactory);
    }

    /**
     * Get the single admin port. If there is not exactly one, throw an exception.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get single admin port from
     * @return the admin port
     * @throws IllegalStateException if there is not exactly one admin port
     */
    public static Port getOnlyAdminPort(DefaultServerFactory serverFactory) {
        var adminPorts = getAdminPorts(serverFactory);
        checkExactlyOnePort("admin", adminPorts);
        return first(adminPorts);
    }

    private static void checkExactlyOnePort(String portType, List<Port> ports) {
        var numPorts = ports.size();
        checkState(numPorts == 1, "expected only one %s port but found %s", portType, numPorts);
    }

    /**
     * Find only the admin ports.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @return the admin ports
     */
    public static <C extends Configuration> List<Port> getAdminPorts(C configuration) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getPorts(serverFactory, PortType.ADMIN);
    }

    /**
     * Find only the admin ports.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the admin ports from
     * @return the admin ports
     */
    public static List<Port> getAdminPorts(DefaultServerFactory serverFactory) {
        return getPorts(serverFactory, PortType.ADMIN);
    }

    /**
     * Find all the {@link Port}s having the given type.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @param portType the type of port to find
     * @return a list containing the matched ports
     */
    public static <C extends Configuration> List<Port> getPorts(C configuration, PortType portType) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getPorts(serverFactory, portType);
    }

    /**
     * Find all the {@link Port}s having the given type.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the ports from
     * @param portType the type of port to find
     * @return a list containing the matched ports
     */
    public static List<Port> getPorts(DefaultServerFactory serverFactory, PortType portType) {
        return Arrays.stream(ConnectorType.values())
                .map(connectorType -> getPort(serverFactory, portType, connectorType))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Get the {@link Port} having the given type and connector type.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @param portType the type of port to find
     * @param connectorType the connector type to find
     * @return an {@link Optional} containing the matching port or {@code Optional#empty()}
     */
    public static <C extends Configuration> Optional<Port> getPort(C configuration,
                                                                   PortType portType,
                                                                   ConnectorType connectorType) {
        var serverFactory = requireDefaultServerFactory(configuration);
        return getPort(serverFactory, portType, connectorType);
    }

    /**
     * Get the {@link Port} having the given type and connector type.
     *
     * @param serverFactory the {@link DefaultServerFactory} to get the ports from
     * @param portType the type of port to find
     * @param connectorType the connector type to find
     * @return an {@link Optional} containing the matching port or {@code Optional#empty()}
     */
    public static Optional<Port> getPort(DefaultServerFactory serverFactory,
                                         PortType portType,
                                         ConnectorType connectorType) {

        Optional<Integer> port = (portType == PortType.APPLICATION) ?
                getApplicationPort(serverFactory, connectorType) : getAdminPort(serverFactory, connectorType);

        return port.map(portNum -> newPort(portType, connectorType, portNum));
    }

    /**
     * Get the application port from the given Dropwizard configuration that has the given connector type.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @param connectorType the connector type to find
     * @return an {@link Optional} containing the matching port number or {@code Optional#empty()}
     */
    public static <C extends Configuration> Optional<Integer> getApplicationPort(C configuration, ConnectorType connectorType) {
        var defaultServerFactory = requireDefaultServerFactory(configuration);
        return getApplicationPort(defaultServerFactory, connectorType);
    }

    /**
     * Get the admin port from the given Dropwizard configuration that has the given connector type.
     *
     * @param <C> the type of the Dropwizard Configuration
     * @param configuration the Dropwizard configuration
     * @param connectorType the connector type to find
     * @return an {@link Optional} containing the matching port number or {@code Optional#empty()}
     */
    public static <C extends Configuration> Optional<Integer> getAdminPort(C configuration, ConnectorType connectorType) {
        var defaultServerFactory = requireDefaultServerFactory(configuration);
        return getAdminPort(defaultServerFactory, connectorType);
    }

    /**
     * Create a new {@link Port} instance.
     *
     * @param portType the type of port
     * @param connectorType the connector type for the port
     * @param portNumber the port number
     * @return a new Port instance
     */
    public static Port newPort(PortType portType, ConnectorType connectorType, Integer portNumber) {
        checkArgumentNotNull(portNumber, "portNumber must not be null");
        return Port.of(portNumber, portType, Port.Security.fromScheme(connectorType.name()));
    }

    /**
     * Determines the application port for the Dropwizard server that matches the given connectorType
     *
     * @param serverFactory The {@link DefaultServerFactory} to get the connectors from
     * @param connectorType The type of connector that is required
     * @return an {@link Optional} containing the matching port or {@code Optional#empty()}
     */
    public static Optional<Integer> getApplicationPort(DefaultServerFactory serverFactory, ConnectorType connectorType) {
        var connector = getApplicationConnector(serverFactory, connectorType);
        return getPort(connector);
    }

    private static HttpConnectorFactory getApplicationConnector(DefaultServerFactory serverFactory, ConnectorType connectorType) {
        var connectors = serverFactory.getApplicationConnectors();
        return getConnectorFactory(connectorType, connectors);
    }

    /**
     * Determines the admin port for the Dropwizard server that matches the given connectorType
     *
     * @param serverFactory The {@link DefaultServerFactory} to get the connectors from
     * @param connectorType The type of connector that is required
     * @return an {@link Optional} containing the matching port or {@code Optional#empty()}
     */
    public static Optional<Integer> getAdminPort(DefaultServerFactory serverFactory, ConnectorType connectorType) {
        var connector = getAdminConnector(serverFactory, connectorType);
        return getPort(connector);
    }

    private static HttpConnectorFactory getAdminConnector(DefaultServerFactory serverFactory, ConnectorType connectorType) {
        var connectors = serverFactory.getAdminConnectors();
        return getConnectorFactory(connectorType, connectors);
    }

    private static HttpConnectorFactory getConnectorFactory(ConnectorType connectorType, List<ConnectorFactory> connectors) {
        var connectorsByType = createConnectorFactoryMap(connectors);
        return connectorsByType.get(connectorType);
    }

    /**
     * This method assumes there will be only one connector factory for each connector type. For example, an application
     * with HTTPS application and admin ports. Or an application with HTTP application and admin ports. Or even an
     * application with both HTTPS and HTTP application and admin ports. So if the given list of connectors contains
     * more than one connector factory of a given type, for example, two HTTPS connector factories, we will always return
     * the last one in the list. Our reasoning is that it makes little sense to us for a Dropwizard (or any) web
     * service to run on multiple HTTPS application ports. Maybe there is some good reason to do that, but we've never
     * seen one. This is why the merge function below always returns the second factory.
     */
    @VisibleForTesting
    static Map<ConnectorType, HttpConnectorFactory> createConnectorFactoryMap(List<ConnectorFactory> connectors) {
        return connectors.stream()
                .filter(HttpConnectorFactory.class::isInstance)
                .map(HttpConnectorFactory.class::cast)
                .collect(toMap(ConnectorType::forHttpConnectorFactory, identity(), DropwizardConnectors::last));
    }

    private static HttpConnectorFactory last(HttpConnectorFactory factory1, HttpConnectorFactory factory2) {
        LOG.warn("There is more than one ConnectorFactory for a given type (HTTP/HTTPS). We currently do not support" +
                        " this and are returning the last one we are given to provide deterministic behavior. If you see this" +
                        " message, your application is defining multiple application or admin connectors for the same type, for" +
                        " example two separate HTTPS application ports. Note also will see this message (N-1) times if there" +
                        " are N ConnectorFactory instances for the same type. Using factory {} with port {}. Discarding factory {}" +
                        " with port {}",
                factory2.getClass().getSimpleName(), factory2.getPort(), factory1.getClass().getSimpleName(), factory1.getPort());

        return factory2;
    }

    private static Optional<Integer> getPort(HttpConnectorFactory connector) {
        return Optional.ofNullable(connector)
                .map(HttpConnectorFactory::getPort);
    }
}
