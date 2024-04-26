package org.kiwiproject.dropwizard.util.bundle;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.curator.config.CuratorConfig;

import java.beans.ConstructorProperties;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for startup locks which is used by {@link StartupLockBundle}.
 */
@Data
public class StartupLockConfiguration {

    /**
     * Default path for ZooKeeper startup locks.
     */
    @SuppressWarnings("java:S1075")
    public static final String DEFAULT_ZK_STARTUP_LOCK_PATH = "/kiwi-startup-locks";

    /**
     * Default timeout for ZooKeeper startup locks.
     */
    public static final Duration DEFAULT_ZK_STARTUP_LOCK_TIMEOUT = Duration.minutes(1);

    /**
     * Whether ports are being assigned randomly during startup.
     * <p>
     * The default value is true.
     * <p>
     * When this is true, the lock will be attempted. When it is false,
     * the lock will <em>not</em> be attempted.
     * <p>
     * Even if you are <em>not</em> using the {@link StartupLockBundle} in
     * conjunction with dynamic ports, you can ensure the lock will be attempted
     * by setting this property to true.
     */
    private boolean useDynamicPorts;

    /**
     * The base lock path for ZooKeeper startup locks. The IP address is appended dynamically at runtime.
     * <p>
     * The default value is {@link #DEFAULT_ZK_STARTUP_LOCK_PATH}.
     */
    @NotBlank
    private String zkStartupLockPath;

    /**
     * The maximum time to wait on startup ZooKeeper lock acquisition before timing out.
     * <p>
     * The default value is {@link #DEFAULT_ZK_STARTUP_LOCK_TIMEOUT}.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    private Duration zkStartupLockTimeout;

    /**
     * The configuration to use for Apache Curator, which is used to acquire the lock.
     * <p>
     * The default value is an instance created using the no-args constructor,
     * which will result in the ZooKeeper conection string {@code "localhost:2181"}.
     * This only makes sense if you are using a single-node ZooKeeper "cluster"
     * on each server where your Dropwizard applications run, otherwise you should
     * provide a ZooKeeper connection string to a real cluster (ensemble).
     */
    @NotNull
    @Valid
    @JsonProperty("curator")
    private CuratorConfig curatorConfig;

    public StartupLockConfiguration() {
        this(null, null, null, null);
    }

    @Builder
    @ConstructorProperties({ "useDynamicPorts", "zkStartupLockPath", "zkStartupLockTimeout", "curatorConfig" })
    public StartupLockConfiguration(Boolean useDynamicPorts,
                                    String zkStartupLockPath,
                                    Duration zkStartupLockTimeout,
                                    CuratorConfig curatorConfig) {

        this.useDynamicPorts = toBooleanOrTrue(useDynamicPorts);
        this.zkStartupLockPath = defaultIfBlank(zkStartupLockPath, DEFAULT_ZK_STARTUP_LOCK_PATH);
        this.zkStartupLockTimeout = requireNonNullElse(zkStartupLockTimeout, DEFAULT_ZK_STARTUP_LOCK_TIMEOUT);
        this.curatorConfig = requireNonNullElseGet(curatorConfig, CuratorConfig::new);
    }

    private static boolean toBooleanOrTrue(@Nullable Boolean booleanObject) {
        return isNull(booleanObject) ? true : booleanObject.booleanValue();
    }
}
