package org.kiwiproject.dropwizard.util.startup;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.format;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * Finds random application and admin ports in an {@link AllowablePortRange}.
 */
@JsonTypeName("random")
@Slf4j
public class RandomFreePortFinder implements FreePortFinder {

    private final LocalPortChecker localPortChecker;
    private final RandomGenerator randomGenerator = RandomGenerator.getDefault();

    /**
     * Create a new instance.
     */
    public RandomFreePortFinder() {
        this(new LocalPortChecker());
    }

    /**
     * Create a new instance.
     *
     * @param localPortChecker the port checker to use
     */
    public RandomFreePortFinder(LocalPortChecker localPortChecker) {
        this.localPortChecker = requireNotNull(localPortChecker, "localPortChecker must not be null");
    }

    /**
     * Find application and admin ports randomly.
     * <p>
     * If {@code portRange} is {@code null}, then the application and admin
     * ports will both be zero. This will result in the ports being randomly
     * selected.
     * <p>
     * This method only returns a {@link ServicePorts} instance when there two open
     * ports in the range. Otherwise, it throws a {@link NoAvailablePortException}.
     *
     * @param portRange the allowable port range, or {@code null}
     * @return a new {@link ServicePorts} instance
     * @throws NoAvailablePortException if no open port was found in the allowable port range
     */
    @Override
    public ServicePorts find(@Nullable AllowablePortRange portRange) {
        if (isNull(portRange)) {
            return new ServicePorts(0, 0);
        }

        var usedPorts = new HashSet<Integer>();
        var applicationPort = findFreePort(portRange, usedPorts);
        var adminPort = findFreePort(portRange, usedPorts);

        return new ServicePorts(applicationPort, adminPort);
    }

    /**
     * @implNote Mutates {@code usedPorts} for each used port it finds
     */
    private int findFreePort(AllowablePortRange portRange, Set<Integer> usedPorts) {
        IntSupplier portSupplier = () -> portRange.getMinPortNumber() +
                randomGenerator.nextInt(portRange.getNumPortsInRange());

        var assignedPort = IntStream.generate(portSupplier)
                .limit(portRange.getMaxPortCheckAttempts())
                .filter(port -> availableAndUnused(port, usedPorts))
                .findFirst();

        if (assignedPort.isPresent()) {
            usedPorts.add(assignedPort.getAsInt());
            return assignedPort.getAsInt();
        }

        var message = format("Could not find an available port between {} and {} after {} attempts. I give up.",
                portRange.getMinPortNumber(),
                portRange.getMaxPortNumber(),
                portRange.getMaxPortCheckAttempts());
        throw new NoAvailablePortException(message);
    }

    private boolean availableAndUnused(int port, Set<Integer> usedPorts) {
        LOG.trace("Checking if port {} is unused and available", port);
        return !usedPorts.contains(port) && localPortChecker.isPortAvailable(port);
    }
}
