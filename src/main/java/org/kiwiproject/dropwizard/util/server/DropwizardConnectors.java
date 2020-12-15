package org.kiwiproject.dropwizard.util.server;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import lombok.experimental.UtilityClass;

/**
 * Utility class that assists with setting up the server connectors in Dropwizard.
 */
@UtilityClass
public class DropwizardConnectors {

    /**
     * Enum defining the possible options for a connector type in Dropwizard.
     */
    public enum ConnectorType {
        HTTP(HttpConnectorFactory.class), HTTPS(HttpsConnectorFactory.class);

        private final Class<? extends ConnectorFactory> connectorClass;

        ConnectorType(Class<? extends ConnectorFactory> connectorClass) {
            this.connectorClass = connectorClass;
        }

        static ConnectorType forClass(Class<? extends ConnectorFactory> connectorClass) {
            return Arrays.stream(ConnectorType.values())
                    .filter(type -> type.connectorClass == connectorClass)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unable to find ConnectorType for " + connectorClass.getName()));
        }
    }

    /**
     * Requires that the given {@link ServerFactory} is in fact a {@link DefaultServerFactory}.
     *
     * @param serverFactory {@link ServerFactory} to check to make sure it is a {@link DefaultServerFactory}
     * @return the given server factory if it is an instance of {@link DefaultServerFactory}
     * @throws IllegalStateException if serverFactory is not a {@link DefaultServerFactory}
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
        return (HttpConnectorFactory) connectorsByType.get(connectorType);
    }

    @VisibleForTesting
    static Map<ConnectorType, ConnectorFactory> createConnectorFactoryMap(List<ConnectorFactory> connectors) {
        return connectors.stream()
                .filter(factory -> factory instanceof HttpConnectorFactory)
                .collect(toMap(
                        factory -> ConnectorType.forClass(factory.getClass()),
                        identity()
                ));
    }

    private static Optional<Integer> getPort(HttpConnectorFactory connector) {
        return Optional.ofNullable(connector)
                .map(HttpConnectorFactory::getPort);
    }
}
