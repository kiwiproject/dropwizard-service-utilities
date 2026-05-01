package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("AbstractCachingHealthCheck")
class AbstractCachingHealthCheckTest {

    private KiwiEnvironment kiwiEnvironment;

    @BeforeEach
    void setUp() {
        kiwiEnvironment = mock(KiwiEnvironment.class);
    }

    @Nested
    class Construction {

        @Test
        void shouldCreateWithCustomExpirationAndCachingEnabled() {
            var twoMinutes = Duration.ofMinutes(2);
            var healthCheck = new HappyHealthCheck(twoMinutes, true);
            assertThat(healthCheck.cacheExpiration()).isEqualTo(twoMinutes);
            assertThat(healthCheck.isCachingEnabled()).isTrue();
        }

        @Test
        void shouldCreateWithCustomExpirationAndCachingDisabled() {
            var tenSeconds = Duration.ofSeconds(10);
            var healthCheck = new HappyHealthCheck(tenSeconds, false);
            assertThat(healthCheck.cacheExpiration()).isEqualTo(tenSeconds);
            assertThat(healthCheck.isCachingEnabled()).isFalse();
        }
    }

    @Nested
    class WithCachingEnabled {

        @Test
        void shouldReturnCachedResult_WhenCacheHasNotExpired() {
            var expiration = Duration.ofSeconds(30);
            var healthCheck = new HappyHealthCheck(expiration, true, kiwiEnvironment);

            var firstCheckTime = Instant.now();
            var secondCheckTime = firstCheckTime.plusSeconds(12);
            var thirdCheckTime = firstCheckTime.plusSeconds(29);

            when(kiwiEnvironment.currentTimeMillis())
                    .thenReturn(firstCheckTime.toEpochMilli())
                    .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during TimestampedResult creation
                    .thenReturn(secondCheckTime.toEpochMilli())
                    .thenReturn(thirdCheckTime.toEpochMilli());

            var expectedMessage = "Everything is awesome! Count: 1";

            // First check should invoke doCheck
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage(expectedMessage);

            // Second and third checks should return the same (cached) result
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage(expectedMessage);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage(expectedMessage);

            verify(kiwiEnvironment, times(4)).currentTimeMillis();
            verifyNoMoreInteractions(kiwiEnvironment);
        }

        @Test
        void shouldExecuteDoCheck_WhenCacheHasExpired() {
            var expiration = Duration.ofSeconds(30);
            var healthCheck = new HappyHealthCheck(expiration, true, kiwiEnvironment);

            var firstCheckTime = Instant.now();
            var secondCheckTime = firstCheckTime.plusSeconds(15);
            var thirdCheckTime = firstCheckTime.plusSeconds(31);

            when(kiwiEnvironment.currentTimeMillis())
                    .thenReturn(firstCheckTime.toEpochMilli())
                    .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during TimestampedResult creation
                    .thenReturn(secondCheckTime.toEpochMilli())
                    .thenReturn(thirdCheckTime.toEpochMilli())
                    .thenReturn(thirdCheckTime.toEpochMilli() + smallDelay());  // during TimestampedResult creation

            // First check invokes doCheck
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("Everything is awesome! Count: 1");

            // Second check returns cached result
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("Everything is awesome! Count: 1");

            // Third check invokes doCheck again after cache expiration
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("Everything is awesome! Count: 2");

            verify(kiwiEnvironment, times(5)).currentTimeMillis();
            verifyNoMoreInteractions(kiwiEnvironment);
        }

        @Test
        void shouldCatchExceptionsFromDoCheck_AndReturnUnhealthyResult() {
            var expiration = Duration.ofMinutes(10);
            var healthCheck = new BadHealthCheck(expiration, true, kiwiEnvironment);

            var firstCheckTime = Instant.now();
            var secondCheckTime = firstCheckTime.plusSeconds(5);

            when(kiwiEnvironment.currentTimeMillis())
                    .thenReturn(firstCheckTime.toEpochMilli())
                    .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during TimestampedResult creation
                    .thenReturn(secondCheckTime.toEpochMilli());

            var expectedMessage = f("{}#doCheck threw an exception. Exception: java.lang.RuntimeException, Message: Oops. Count: 1",
                    BadHealthCheck.class.getName());

            // First check invokes doCheck and catches the exception
            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(expectedMessage);

            // Second check returns the cached unhealthy result
            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(expectedMessage);

            verify(kiwiEnvironment, times(3)).currentTimeMillis();
            verifyNoMoreInteractions(kiwiEnvironment);
        }
    }

    @Nested
    class WithCachingDisabled {

        @Test
        void shouldAlwaysInvokeDoCheck() {
            var healthCheck = new HappyHealthCheck(AbstractCachingHealthCheck.DEFAULT_CACHE_EXPIRATION_DURATION, false);

            assertThatHealthCheck(healthCheck).isHealthy().hasMessage("Everything is awesome! Count: 1");
            assertThatHealthCheck(healthCheck).isHealthy().hasMessage("Everything is awesome! Count: 2");
            assertThatHealthCheck(healthCheck).isHealthy().hasMessage("Everything is awesome! Count: 3");
        }

        @Test
        void shouldCatchExceptionsFromDoCheck_AndReturnUnhealthyResult() {
            var healthCheck = new BadHealthCheck(AbstractCachingHealthCheck.DEFAULT_CACHE_EXPIRATION_DURATION, false);

            var expectedMessage = f("{}#doCheck threw an exception. Exception: java.lang.RuntimeException, Message: Oops. Count: 1",
                    BadHealthCheck.class.getName());

            assertThatHealthCheck(healthCheck).isUnhealthy().hasMessage(expectedMessage);
        }
    }

    private static long smallDelay() {
        return ThreadLocalRandom.current().nextLong(5, 10);
    }

    static class HappyHealthCheck extends AbstractCachingHealthCheck {
        private final AtomicInteger count = new AtomicInteger();

        HappyHealthCheck(Duration cacheExpiration, boolean cachingEnabled) {
            super(cacheExpiration, cachingEnabled);
        }

        HappyHealthCheck(Duration cacheExpiration, boolean cachingEnabled, KiwiEnvironment kiwiEnvironment) {
            super(cacheExpiration, cachingEnabled, kiwiEnvironment);
        }

        @Override
        protected HealthCheck.Result doCheck() {
            return Result.healthy("Everything is awesome! Count: " + count.incrementAndGet());
        }
    }

    static class BadHealthCheck extends AbstractCachingHealthCheck {
        private final AtomicInteger count = new AtomicInteger();

        BadHealthCheck(Duration cacheExpiration, boolean cachingEnabled) {
            super(cacheExpiration, cachingEnabled);
        }

        BadHealthCheck(Duration cacheExpiration, boolean cachingEnabled, KiwiEnvironment kiwiEnvironment) {
            super(cacheExpiration, cachingEnabled, kiwiEnvironment);
        }

        @Override
        protected HealthCheck.Result doCheck() {
            throw new RuntimeException("Oops. Count: " + count.incrementAndGet());
        }
    }
}
