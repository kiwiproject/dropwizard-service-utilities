package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

/**
 * Finds application and admin ports in an {@link AllowablePortRange}
 * by traversing through each port number sequentially, and ensuring
 * that the ports are adjacent.
 */
@Slf4j
public class AdjacentFreePortFinder implements FreePortFinder {

    private final LocalPortChecker localPortChecker;

    /**
     * Create a new instance.
     */
    public AdjacentFreePortFinder() {
        this(new LocalPortChecker());
    }

    /**
     * Create a new instance.
     *
     * @param localPortChecker the port checker to use
     */
    public AdjacentFreePortFinder(LocalPortChecker localPortChecker) {
        this.localPortChecker = requireNotNull(localPortChecker, "localPortChecker must not be null");
    }

    /**
     * Find the first two open and adjacent ports in the given port range, assigning them to
     * the application and admin ports, respectively.
     * <p>
     * This method only returns a {@link ServicePorts} instance when there are adjacent ports
     * in the port range. Otherwise, it throws a {@link NoAvailablePortException}.
     *
     * @param portRange the allowable port range
     * @return a new {@link ServicePorts} instance
     * @throws NoAvailablePortException if two open adjacent ports were not found in the allowable port range
     */
    @Override
    public ServicePorts find(AllowablePortRange portRange) {
        checkArgumentNotNull(portRange, "portRange must not be null");

        var applicationPort = portRange.getMinPortNumber();
        var maxApplicationPort = portRange.getMaxPortNumber();

        while (applicationPort < maxApplicationPort) {
            LOG.trace("At top of loop with applicationPort: {}", applicationPort);

            // If the application port isn't open, skip the remaining admin port check
            if (portIsNotOpen(applicationPort)) {
                LOG.trace("applicationPort {} is not open, so increment it by one and continue", applicationPort);
                ++applicationPort;
                continue;
            }

            // The application port is available, so check if the next port is open.
            // If it is, we've got two adjacent open ports and can stop checking.
            var adminPort = applicationPort + 1;
            if (portIsOpen(adminPort)) {
                LOG.trace("applicationPort {} and adminPort {} are both open, so use them and return",
                        applicationPort, adminPort);
                return new ServicePorts(applicationPort, adminPort);
            }

            // Since the admin port was not open, set the next application port to
            // the port following it to avoid checking the same (closed) port twice.
            var originalApplicationPort = applicationPort;
            applicationPort = adminPort + 1;
            LOG.trace("applicationPort {} is open, but adminPort {} is not, so set applicationPort to {}",
                    originalApplicationPort, adminPort, applicationPort);
        }

        LOG.trace("applicationPort {} is at the end of the port range, so there is no way to get an admin port",
                applicationPort);

        var message = f("Could not find two adjacent open ports between {} and {}",
                portRange.getMinPortNumber(),
                portRange.getMaxPortNumber());
        throw new NoAvailablePortException(message);
    }

    private boolean portIsNotOpen(int port) {
        return !portIsOpen(port);
    }

    private boolean portIsOpen(int port) {
        return localPortChecker.isPortAvailable(port);
    }
}
