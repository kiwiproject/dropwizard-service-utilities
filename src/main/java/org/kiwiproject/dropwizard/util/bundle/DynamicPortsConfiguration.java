package org.kiwiproject.dropwizard.util.bundle;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiBooleans.toBooleanOrTrue;
import static org.kiwiproject.base.KiwiIntegers.toIntOrDefault;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.config.TlsContextConfiguration;
import org.kiwiproject.dropwizard.util.startup.FreePortFinder;
import org.kiwiproject.dropwizard.util.startup.RandomFreePortFinder;

import java.beans.ConstructorProperties;

/**
 * Configuration class for dynamic ports used by {@link DynamicPortsBundle}.
 */
@Data
public class DynamicPortsConfiguration {

    private static final int DEFAULT_MIN_DYNAMIC_PORT = 1_024;
    private static final int DEFAULT_MAX_DYNAMIC_PORT = 65_535;

    /**
     * Whether to assign ports dynamically between {@code minDynamicPort} and {@code maxDynamicPort}.
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
     * The default value is 65,535 (the highest available port).
     */
    @Positive
    @Max(DEFAULT_MAX_DYNAMIC_PORT)
    private int maxDynamicPort;

    /**
     * Defines how ports should be chosen when assigning them dynamically.
     * <p>
     * The default is to assign them randomly using a {@link RandomFreePortFinder}.
     * <p>
     * This is resolved via Jackson polymorphic deserialization. Implementations
     * must be annotated with `@JsonTypeName` where the value is the logical name.
     * In your application's configuration you can then configure the implementation
     * using the {@code type} property, which must match one of the logical names.
     * There are three implementations available in this library with logical
     * names "adjacent", "incrementing", and "random" (the default). Here is
     * an example configuration:
     * <pre>
     * freePortFinder:
     *  type: adjacent
     * </pre>
     */
    private FreePortFinder freePortFinder;

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

    /**
     * Create a new instance using default values.
     */
    public DynamicPortsConfiguration() {
        this(null, null, null, null, null, null);
    }

    /**
     * Create a new instance using all values.
     * <p>
     * Consider using the {@link #builder()} instead.
     *
     * @param useDynamicPorts whether ports be assigned dynamically
     * @param useSecureDynamicPorts if using dynamic ports, should they use HTTP?
     * @param minDynamicPort the minimum port that can be assigned
     * @param maxDynamicPort the maximum port that can be assigned
     * @param freePortFinder defines how to assign application and admin ports
     * @param tlsContextConfiguration the TLS configuration to use
     */
    @Builder
    @ConstructorProperties(
        { "useDynamicPorts", "useSecureDynamicPorts", "minDynamicPort", "maxDynamicPort", "freePortFinder", "tlsContextConfiguration" }
    )
    public DynamicPortsConfiguration(@Nullable Boolean useDynamicPorts,
                                     @Nullable Boolean useSecureDynamicPorts,
                                     @Nullable Integer minDynamicPort,
                                     @Nullable Integer maxDynamicPort,
                                     @Nullable FreePortFinder freePortFinder,
                                     @Nullable TlsContextConfiguration tlsContextConfiguration) {

        this.useDynamicPorts = toBooleanOrTrue(useDynamicPorts);
        this.useSecureDynamicPorts = toBooleanOrTrue(useSecureDynamicPorts);
        this.minDynamicPort = toIntOrDefault(minDynamicPort, DEFAULT_MIN_DYNAMIC_PORT);
        this.maxDynamicPort = toIntOrDefault(maxDynamicPort, DEFAULT_MAX_DYNAMIC_PORT);
        this.freePortFinder = isNull(freePortFinder) ? new RandomFreePortFinder() : freePortFinder;
        this.tlsContextConfiguration = tlsContextConfiguration;
    }

}
