package org.kiwiproject.dropwizard.util.startup;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.jackson.Discoverable;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;

/**
 * Defines a contract for finding application and admin ports for a service.
 * <p>
 * This is a {@link FunctionalInterface} so that it can be created with
 * lambda expressions, method references, or constructor references.
 */
@FunctionalInterface
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = RandomFreePortFinder.class)
public interface FreePortFinder extends Discoverable {

    /**
     * Find application and admin ports.
     *
     * @param portRange the allowable port range
     * @return a new {@link ServicePorts} instance
     * @throws NoAvailablePortException if no open ports were found in the allowable port range
     */
    ServicePorts find(AllowablePortRange portRange);

    /**
     * A record that represents the application and admin ports for a Dropwizard application.
     *
     * @param applicationPort the application port
     * @param adminPort       the admin port
     */
    record ServicePorts(int applicationPort, int adminPort) {
    }
}
