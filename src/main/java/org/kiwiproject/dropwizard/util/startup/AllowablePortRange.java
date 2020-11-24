package org.kiwiproject.dropwizard.util.startup;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.base.KiwiPreconditions.requireValidPort;

import lombok.Getter;

/**
 * Defines the allowable port range for a service to bind to, in situations where there is a restriction.
 */
@Getter
public class AllowablePortRange {

    private static final int RANGE_MULTIPLIER = 3;

    int minPortNumber;
    int maxPortNumber;
    int numPortsInRange;
    int maxPortCheckAttempts;

    /**
     * Creates a new AllowablePortRange
     *
     * @param minPortNumber The minimum port allowed in the range
     * @param maxPortNumber The maximum port allowed in the range
     * @throws IllegalStateException if the minPortNumber is greater than or equal to the maxPortNumber or if either port is not valid
     */
    public AllowablePortRange(int minPortNumber, int maxPortNumber) {
        checkState(minPortNumber < maxPortNumber, "minPortNumber must be less than maxPortNumber");

        this.minPortNumber = requireValidPort(minPortNumber);
        this.maxPortNumber = requireValidPort(maxPortNumber);

        this.numPortsInRange = 1 + (maxPortNumber - minPortNumber);
        this.maxPortCheckAttempts = RANGE_MULTIPLIER * numPortsInRange;
    }
}
