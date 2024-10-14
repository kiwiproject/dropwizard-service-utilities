package org.kiwiproject.dropwizard.util.startup;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.base.KiwiPreconditions.requireValidNonZeroPort;

import lombok.Getter;
import lombok.ToString;

/**
 * Defines the allowable port range for a service to bind to, for situations where there is a restriction.
 */
@Getter
@ToString
public class AllowablePortRange {

    private static final int RANGE_MULTIPLIER = 3;

    final int minPortNumber;
    final int maxPortNumber;
    final int numPortsInRange;
    final int maxPortCheckAttempts;

    /**
     * Creates a new AllowablePortRange
     *
     * @param minPortNumber The minimum port allowed in the range (inclusive)
     * @param maxPortNumber The maximum port allowed in the range (inclusive)
     * @throws IllegalStateException if the minPortNumber is greater than or equal to the maxPortNumber,
     * or if either port is not valid
     */
    public AllowablePortRange(int minPortNumber, int maxPortNumber) {
        checkState(minPortNumber < maxPortNumber,
                "minPortNumber must be less than maxPortNumber (was: %s -> %s)", minPortNumber, maxPortNumber);

        this.minPortNumber = requireValidNonZeroPort(minPortNumber,
                "minPort must be between 1 and 65534 (was: %s)", minPortNumber);

        this.maxPortNumber = requireValidNonZeroPort(maxPortNumber,
                "maxPort must be between 2 and 65535 (was: %s)", maxPortNumber);

        this.numPortsInRange = 1 + (maxPortNumber - minPortNumber);
        this.maxPortCheckAttempts = RANGE_MULTIPLIER * numPortsInRange;
    }
}
