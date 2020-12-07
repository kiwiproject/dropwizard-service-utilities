package org.kiwiproject.dropwizard.util.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiDoubles.isZero;

import lombok.Builder;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;

/**
 * Configuration to use for the {@link HttpConnectionsHealthCheck}
 */
@Builder
public class HttpHealthCheckConfig {

    private final String name;
    private final double warningThreshold;

    public String getName() {
        return isBlank(name) ? HttpConnectionsHealthCheck.DEFAULT_NAME : name;
    }

    public double getWarningThreshold() {
        return (isZero(warningThreshold) || warningThreshold < 0.0) ?
                HttpConnectionsHealthCheck.DEFAULT_WARNING_THRESHOLD : warningThreshold;
    }

}
