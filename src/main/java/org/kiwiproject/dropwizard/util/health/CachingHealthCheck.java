package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.metrics.health.HealthCheckResults;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link HealthCheck} that caches the result of another HealthCheck
 * for a certain period of time.
 * <p>
 * Use this if a HealthCheck is expensive to compute, and you want to minimize
 * the execution cost. For example, a HealthCheck that makes network calls might
 * be a good candidate for caching if health checks are executed frequently.
 */
@Slf4j
public class CachingHealthCheck extends HealthCheck {

    /**
     * The default cache expiration, during which period the same Result will be returned (5 minutes).
     */
    public static final Duration DEFAULT_CACHE_EXPIRATION_DURATION = Duration.ofMinutes(5);

    record TimestampedResult(long epochMillis, HealthCheck.Result result) {
    }

    /**
     * A name for this CachingHealthCheck.
     * <p>
     * Usually this should be the name given to the wrapped HealthCheck
     * so that it can be easily distinguished from others.
     */
    @Getter
    @Accessors(fluent = true)
    private final String name;

    /**
     * The wrapped HealthCheck whose results will be cached.
     */
    @Getter
    @Accessors(fluent = true)
    private final HealthCheck healthCheck;

    private final long cacheExpirationMillis;

    private final KiwiEnvironment kiwiEnvironment;

    private final AtomicReference<TimestampedResult> lastResultReference;

    /**
     * Create a new instance with the default cache expiration.
     *
     * @param name the health check name
     * @param healthCheck the HealthCheck whose results will be cached
     * @see #DEFAULT_CACHE_EXPIRATION_DURATION
     */
    public CachingHealthCheck(String name, HealthCheck healthCheck) {
        this(name, healthCheck, DEFAULT_CACHE_EXPIRATION_DURATION);
    }

    /**
     * Create a new instance.
     *
     * @param name the health check name
     * @param healthCheck the HealthCheck whose results will be cached
     * @param cacheExpiration the cache expiration period
     */
    public CachingHealthCheck(String name, HealthCheck healthCheck, Duration cacheExpiration) {
        this(name, healthCheck, cacheExpiration, new DefaultEnvironment());
    }

    @VisibleForTesting
    CachingHealthCheck(String name,
                       HealthCheck healthCheck,
                       Duration cacheExpiration,
                       KiwiEnvironment kiwiEnvironment) {
        this.name = requireNotBlank(name);
        this.healthCheck = requireNotNull(healthCheck);
        this.cacheExpirationMillis = requireNotNull(cacheExpiration).toMillis();
        this.kiwiEnvironment = kiwiEnvironment;
        this.lastResultReference = new AtomicReference<>(new TimestampedResult(0, null));
    }

    /**
     * Create and register a new CachingHealthCheck that caches for the given HealthCheck
     * using the default cache expiration.
     *
     * @param registry the health check registry
     * @param name the health check name
     * @param healthCheck the HealthCheck whose results will be cached
     * @return a new CachingHealthCheck instance
     * @see #DEFAULT_CACHE_EXPIRATION_DURATION
     */
    @CanIgnoreReturnValue
    public static CachingHealthCheck register(HealthCheckRegistry registry,
                                              String name,
                                              HealthCheck healthCheck) {

        return register(registry, name, healthCheck, DEFAULT_CACHE_EXPIRATION_DURATION);
    }

    /**
     * Create and register a new CachingHealthCheck that caches for the given HealthCheck.
     *
     * @param registry the health check registry
     * @param name the health check name
     * @param healthCheck the HealthCheck whose results will be cached
     * @param cacheExpiration the cache expiration period
     * @return a new CachingHealthCheck instance
     */
    @CanIgnoreReturnValue
    public static CachingHealthCheck register(HealthCheckRegistry registry,
                                              String name,
                                              HealthCheck healthCheck,
                                              Duration cacheExpiration) {

        var cachingHealthCheck = new CachingHealthCheck(name, healthCheck, cacheExpiration);
        registry.register(name, cachingHealthCheck);
        return cachingHealthCheck;
    }

    /**
     * If the time since the last check exceeds the cache expiration,
     * then execute the wrapped HealthCheck and return its Result.
     * <p>
     * Otherwise, return the last Result.
     */
    @Override
    protected Result check() {
        var nowEpochMillis = kiwiEnvironment.currentTimeMillis();
        var lastResult = lastResultReference.get();
        var millisSinceLastCheck = nowEpochMillis - lastResult.epochMillis();

        if (millisSinceLastCheck < cacheExpirationMillis) {
            logTimeSinceLastCheck(millisSinceLastCheck);
            return lastResult.result();
        }

        LOG.debug("Time since last check ({} millis) is greater than cache expiration ({} millis). Do check.",
                millisSinceLastCheck, cacheExpirationMillis);
        var newResult = executeHealthCheck();
        lastResultReference.set(newResult);

        return newResult.result();
    }

    private void logTimeSinceLastCheck(long millisSinceLastCheck) {
        LOG.debug("Time since last check ({} millis) is less than cache expiration ({} millis)." +
                        " Time until next check: {} millis. Returning last result.",
                millisSinceLastCheck,
                cacheExpirationMillis,
                lazy(() -> cacheExpirationMillis - millisSinceLastCheck)
        );
    }

    private TimestampedResult executeHealthCheck() {
        // The default HealthCheck#execute implementation catches Exception, but it
        // is not final and can therefore be overridden. So, we will be conservative
        // and assume an Exception could be thrown in a subclass.

        Result result;
        try {
            result = healthCheck.execute();
        } catch (Exception e) {
            var healthCheckClassName = healthCheck.getClass().getName();
            LOG.warn("'{}' - {}#execute unexpectedly threw an exception", name, healthCheckClassName, e);
            result = HealthCheckResults.newUnhealthyResult(e,
                    "'%s' - %s#execute unexpectedly threw an exception. Exception: %s, Message: %s",
                    name, healthCheckClassName, e.getClass().getName(), e.getMessage());
        }

        return new TimestampedResult(kiwiEnvironment.currentTimeMillis(), result);
    }

    /**
     * @return the cache expiration period of this CachingHealthCheck
     */
    public Duration cacheExpiration() {
        return Duration.ofMillis(cacheExpirationMillis);
    }
}
