package org.kiwiproject.dropwizard.util.metrics;

import static org.kiwiproject.base.KiwiStrings.format;

import com.codahale.metrics.Gauge;

/**
 * A {@link Gauge} that reports the current server load average, as reported by the {@code uptime} command.
 * <p>
 * If the load average was not obtainable for some reason, returns {@link ServerLoadFetcher#NO_VALUE}
 */
public class ServerLoadGauge implements Gauge<String>{

    public static final String NAME = format("{}.{}.load-average",
            ServerLoadGauge.class.getPackage().getName(),
            ServerLoadGauge.class.getSimpleName().replace("Gauge", ""));

    private final ServerLoadFetcher loadAverage = new ServerLoadFetcher();

    @Override
    public String getValue() {
        return loadAverage.get().orElse(ServerLoadFetcher.NO_VALUE);
    }
}
