package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.metrics.health.HealthCheckResults;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An abstract {@link HealthCheck} base class that provides optional result caching.
 * <p>
 * Subclasses implement {@link #doCheck()} to provide the actual health check logic.
 * The {@link #check()} method (which is {@code final}) handles caching: it returns
 * the cached result when within the expiration window, or delegates to {@link #doCheck()}
 * when the cache has expired or caching is disabled.
 * <p>
 * Use this when you are implementing a HealthCheck and want to offer optional caching
 * without requiring callers to wrap your check in a {@link CachingHealthCheck} decorator.
 * This is especially useful for library authors who control the HealthCheck implementation
 * but want to give users the option to enable or disable caching at construction time.
 *
 * @see CachingHealthCheck
 */
@Slf4j
public abstract class AbstractCachingHealthCheck extends HealthCheck {

    /**
     * The default cache expiration duration (5 minutes).
     */
    public static final Duration DEFAULT_CACHE_EXPIRATION_DURATION = Duration.ofMinutes(5);

    record TimestampedResult(long epochMillis, Result result) {
    }

    private final long cacheExpirationMillis;
    private final boolean cachingEnabled;
    private final KiwiEnvironment kiwiEnvironment;
    private final AtomicReference<TimestampedResult> lastResultReference;

    /**
     * Create a new instance.
     *
     * @param cacheExpiration the cache expiration period
     * @param cachingEnabled whether caching is enabled
     */
    protected AbstractCachingHealthCheck(Duration cacheExpiration, boolean cachingEnabled) {
        this(cacheExpiration, cachingEnabled, new DefaultEnvironment());
    }

    @VisibleForTesting
    AbstractCachingHealthCheck(Duration cacheExpiration, boolean cachingEnabled, KiwiEnvironment kiwiEnvironment) {
        this.cacheExpirationMillis = requireNotNull(cacheExpiration).toMillis();
        this.cachingEnabled = cachingEnabled;
        this.kiwiEnvironment = kiwiEnvironment;
        this.lastResultReference = new AtomicReference<>(new TimestampedResult(0, null));
    }

    /**
     * Perform the actual health check logic.
     * <p>
     * Subclasses implement this method. It is called by {@link #check()} when
     * the cache has expired or caching is disabled.
     *
     * @return the result of the health check
     * @throws Exception if the health check encounters an unexpected error
     */
    @SuppressWarnings("java:S112")  // throws Exception mirrors HealthCheck#check() in the metrics library
    protected abstract Result doCheck() throws Exception;

    /**
     * If caching is enabled and the cached result has not expired, returns the cached result.
     * Otherwise, delegates to {@link #doCheck()}, caches the new result, and returns it.
     * <p>
     * If caching is disabled, always delegates to {@link #doCheck()}.
     * <p>
     * This method is {@code final}; subclasses must implement {@link #doCheck()} instead.
     */
    @Override
    protected final Result check() {
        if (!cachingEnabled) {
            return executeDoCheck();
        }

        var nowEpochMillis = kiwiEnvironment.currentTimeMillis();
        var lastResult = lastResultReference.get();
        var millisSinceLastCheck = nowEpochMillis - lastResult.epochMillis();

        if (millisSinceLastCheck < cacheExpirationMillis) {
            logTimeSinceLastCheck(millisSinceLastCheck);
            return lastResult.result();
        }

        LOG.debug("Time since last check ({} millis) is greater than cache expiration ({} millis). Do check.",
                millisSinceLastCheck, cacheExpirationMillis);

        return computeAndStoreResult().result();
    }

    private void logTimeSinceLastCheck(long millisSinceLastCheck) {
        LOG.debug("Time since last check ({} millis) is less than cache expiration ({} millis)." +
                        " Time until next check: {} millis. Returning last result.",
                millisSinceLastCheck,
                cacheExpirationMillis,
                lazy(() -> cacheExpirationMillis - millisSinceLastCheck));
    }

    private TimestampedResult computeAndStoreResult() {
        var result = executeDoCheck();
        var timestampedResult = new TimestampedResult(kiwiEnvironment.currentTimeMillis(), result);
        lastResultReference.set(timestampedResult);
        return timestampedResult;
    }

    private Result executeDoCheck() {
        try {
            return doCheck();
        } catch (Exception e) {
            var className = getClass().getName();
            LOG.warn("{}#doCheck threw an exception", className, e);
            return HealthCheckResults.newUnhealthyResult(e,
                    "%s#doCheck threw an exception. Exception: %s, Message: %s",
                    className, e.getClass().getName(), e.getMessage());
        }
    }

    /**
     * @return whether caching is enabled for this health check
     */
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    /**
     * @return the cache expiration period
     */
    public Duration cacheExpiration() {
        return Duration.ofMillis(cacheExpirationMillis);
    }
}
