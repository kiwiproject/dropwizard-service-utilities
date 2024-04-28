package org.kiwiproject.dropwizard.util.bundle;

import static org.kiwiproject.base.KiwiBooleans.toBooleanOrTrue;
import static org.kiwiproject.base.KiwiIntegers.toIntOrDefault;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.config.TlsContextConfiguration;

import java.beans.ConstructorProperties;

/**
 * Configuration class for dynamic ports used by {@link DynamicPortsBundle}.
 */
@Data
public class DynamicPortsConfiguration {

    private static final int DEFAULT_MIN_DYNAMIC_PORT = 1_024;
    private static final int DEFAULT_MAX_DYNAMIC_PORT = 65_535;

    /**
     * Whether to assign ports randomly between {@code minDynamicPort} and {@code maxDynamicPort}.
     * <p>
     * The default value is true.
     */
    private boolean useDynamicPorts;

    /**
     * Whether to start the service securely (require HTTPS connections to this service)
     * when assigning ports dynamically, i.e., when {@code useDynamicPorts} is enabled.
     * <p>
     * The default value is true.
     */
    private boolean useSecureDynamicPorts;

    /**
     * The lowest port that can be assigned when {@code useDynamicPorts} is enabled.
     * <p>
     * The default value is 1024 (the first port after the well-known ports).
     */
    @Positive
    @Max(DEFAULT_MAX_DYNAMIC_PORT)
    private int minDynamicPort;

    /**
     * The highest port that can be assigned when {@code useDynamicPorts} is enabled.
     * <p>
     * The default value is 65353 (the highest available port).
     */
    @Positive
    @Max(DEFAULT_MAX_DYNAMIC_PORT)
    private int maxDynamicPort;

    /**
     * Used when {@code useSecureDynamicPorts} is true (and using dynamic ports).
     * <p>
     * The default value is {@code null}, which will only work when using
     * non-secure (HTTP) ports. Otherwise, a valid TLS configuration must
     * be provided.
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

        this.useDynamicPorts = toBooleanOrTrue(useDynamicPorts);
        this.useSecureDynamicPorts = toBooleanOrTrue(useSecureDynamicPorts);
        this.minDynamicPort = toIntOrDefault(minDynamicPort, DEFAULT_MIN_DYNAMIC_PORT);
        this.maxDynamicPort = toIntOrDefault(maxDynamicPort, DEFAULT_MAX_DYNAMIC_PORT);
        this.tlsContextConfiguration = tlsContextConfiguration;
    }

}
