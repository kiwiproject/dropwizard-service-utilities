package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

import java.util.stream.IntStream;

/**
 * Finds application and admin ports in an {@link AllowablePortRange}
 * by traversing through each port number sequentially.
 */
@JsonTypeName("incrementing")
public class IncrementingFreePortFinder implements FreePortFinder {

    private final LocalPortChecker localPortChecker;

    /**
     * Create a new instance.
     */
    public IncrementingFreePortFinder() {
        this(new LocalPortChecker());
    }

    /**
     * Create a new instance.
     *
     * @param localPortChecker the port checker to use
     */
    public IncrementingFreePortFinder(LocalPortChecker localPortChecker) {
        this.localPortChecker = requireNotNull(localPortChecker, "localPortChecker must not be null");
    }

    /**
     * Find the first two open ports in the given port range, assigning them to
     * the application and admin ports, respectively.
     * <p>
     * The ports may or may not be adjacent, and non-adjacent ports may have one
     * or more ports between them. If two open ports are found, the admin port
     * number will always be higher than the application port number.
     * <p>
     * This method only returns a {@link ServicePorts} instance when there two open
     * ports in the range. Otherwise, it throws a {@link NoAvailablePortException}.
     *
     * @param portRange the allowable port range, or {@code null}
     * @return a new {@link ServicePorts} instance
     * @throws NoAvailablePortException if two open ports were not found in the allowable port range
     */
    @Override
    public ServicePorts find(AllowablePortRange portRange) {
        checkArgumentNotNull(portRange, "portRange must not be null");

        var ports = IntStream.iterate(
                        portRange.getMinPortNumber(),
                        port -> isBelowOrEqualToMaxPort(portRange, port),
                        port -> port + 1)
                .filter(localPortChecker::isPortAvailable)
                .limit(2)
                .toArray();

        if (ports.length == 2) {
            return new ServicePorts(ports[0], ports[1]);
        }

        var message = f("Could not find two open ports between {} and {}",
                portRange.getMinPortNumber(),
                portRange.getMaxPortNumber());
        throw new NoAvailablePortException(message);
    }

    private static boolean isBelowOrEqualToMaxPort(AllowablePortRange portRange, int port) {
        return port <= portRange.getMaxPortNumber();
    }
}
