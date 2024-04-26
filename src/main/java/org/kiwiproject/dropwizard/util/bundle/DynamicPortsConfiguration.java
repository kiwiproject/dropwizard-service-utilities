package org.kiwiproject.dropwizard.util.bundle;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.config.TlsContextConfiguration;

import java.beans.ConstructorProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Configuration class for dynamic ports used by {@link DynamicPortsBundle}.
 */
@Data
public class DynamicPortsConfiguration {

    private static final int DEFAULT_MIN_DYNAMIC_PORT = 1_024;
    private static final int DEFAULT_MAX_DYNAMIC_PORT = 65_535;

    /**
     * Whether to assign ports randomly between {@code minDynamicPort} and {@code maxDynamicPort}.
     */
    private boolean useDynamicPorts;

    /**
     * Whether to start the service securely (require HTTPS connections to this service)
     * when assigning ports dynamically, i.e., when {@code useDynamicPorts} is enabled.
     */
    private boolean useSecureDynamicPorts;

    /**
     * The lowest port that can be assigned when {@code useDynamicPorts} is enabled.
     */
    @Positive
    @Max(DEFAULT_MAX_DYNAMIC_PORT)
    private int minDynamicPort;

    /**
     * The highest port that can be assigned when {@code useDynamicPorts} is enabled.
     */
    @Positive
    @Max(DEFAULT_MAX_DYNAMIC_PORT)
    private int maxDynamicPort;

    /**
     * Used when {@code useSecureDynamicPorts} is true (and using dynamic ports).
     */
    @Nullable
    @JsonProperty("tls")
    @Valid
    private TlsContextConfiguration tlsContextConfiguration;

    public DynamicPortsConfiguration() {
        this(null, null, null, null, null);
    }

    @Builder
    @ConstructorProperties(
        { "useDynamicPorts", "useSecureDynamicPorts", "minDynamicPort", "maxDynamicPort", "tlsContextConfiguration"}
    )
    public DynamicPortsConfiguration(Boolean useDynamicPorts,
                                     Boolean useSecureDynamicPorts,
                                     Integer minDynamicPort,
                                     Integer maxDynamicPort,
                                     @Nullable TlsContextConfiguration tlsContextConfiguration) {

        this.useDynamicPorts = isNull(useDynamicPorts) || useDynamicPorts;
        this.useSecureDynamicPorts = isNull(useSecureDynamicPorts) || useSecureDynamicPorts;
        this.minDynamicPort = isNull(minDynamicPort) ? DEFAULT_MIN_DYNAMIC_PORT : minDynamicPort;
        this.maxDynamicPort = isNull(maxDynamicPort) ? DEFAULT_MAX_DYNAMIC_PORT : maxDynamicPort;
        this.tlsContextConfiguration = tlsContextConfiguration;
    }
}
