package org.kiwiproject.dropwizard.util.startup;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.base.KiwiPreconditions.requireValidPort;

import lombok.Getter;

@Getter
public class PortRangeInfo {

    private static final int RANGE_MULTIPLIER = 3;

    int minPortNumber;
    int maxPortNumber;
    int numPortsInRange;
    int maxPortCheckAttempts;

    public PortRangeInfo(int minPortNumber, int maxPortNumber) {
        checkState(minPortNumber < maxPortNumber, "minPortNumber must be less than maxPortNumber");

        this.minPortNumber = requireValidPort(minPortNumber);
        this.maxPortNumber = requireValidPort(maxPortNumber);

        this.numPortsInRange = 1 + (maxPortNumber - minPortNumber);
        this.maxPortCheckAttempts = RANGE_MULTIPLIER * numPortsInRange;
    }
}
