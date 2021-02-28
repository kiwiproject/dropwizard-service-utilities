package org.kiwiproject.dropwizard.util.server;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        HTTP, HTTPS;

        /**
         * Given an {@link HttpConnectorFactory} instance, determine whether it is for HTTP or HTTPS.
         *
         * @param factory the instance
         * @return the ConnectorType
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
     * Requires that the given {@link ServerFactory} is in fact a {@link DefaultServerFactory}.
     *
     * @param serverFactory {@link ServerFactory} to check to make sure it is a {@link DefaultServerFactory}
     * @return the given server factory if it is an instance of {@link DefaultServerFactory}
     * @throws IllegalStateException    if serverFactory is not a {@link DefaultServerFactory}
     * @throws IllegalArgumentException if serverFactory is null
     */
    public static DefaultServerFactory requireDefaultServerFactory(ServerFactory serverFactory) {
        checkArgumentNotNull(serverFactory, "ServerFactory is required");

        if (serverFactory instanceof DefaultServerFactory) {
            return (DefaultServerFactory) serverFactory;
        }

        var error = format("The server factory is not a {} (it is a {})", DefaultServerFactory.class.getName(), serverFactory.getClass().getName());
        throw new IllegalStateException(error);
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
     * more than one connector factory of a given type, for example two HTTPS connector factories, we will always return
     * the last one in the list. Our reasoning is that it doesn't make much sense to us for a Dropwizard (or any) web
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
