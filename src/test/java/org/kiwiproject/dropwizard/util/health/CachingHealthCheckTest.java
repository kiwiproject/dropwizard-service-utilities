package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.io.KiwiIO;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.MockWebServer;

@DisplayName("CachingHealthCheck")
class CachingHealthCheckTest {

    private MockWebServer server;
    private HappyHealthCheck happyHealthCheck;
    private KiwiEnvironment kiwiEnvironment;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        kiwiEnvironment = mock(KiwiEnvironment.class);
        happyHealthCheck = new HappyHealthCheck();
    }

    @AfterEach
    void tearDown() {
        KiwiIO.closeQuietly(server);
    }

    @Test
    void shouldHaveName() {
        var cachingHealthCheck = new CachingHealthCheck("bad", new BadHealthCheck());
        assertThat(cachingHealthCheck.name()).isEqualTo("bad");
    }

    @Test
    void shouldCreateWithDefaultCacheExpiration() {
        var cachingHealthCheck = new CachingHealthCheck("happy", happyHealthCheck);
        assertThat(cachingHealthCheck.cacheExpiration())
                .isEqualTo(CachingHealthCheck.DEFAULT_CACHE_EXPIRATION_DURATION);
    }

    @Test
    void shouldCreateWithCustomCacheExpiration() {
        var twoMinutes = Duration.ofMinutes(2);
        var cachingHealthCheck = new CachingHealthCheck("happy", happyHealthCheck, twoMinutes);
        assertThat(cachingHealthCheck.cacheExpiration()).isEqualTo(twoMinutes);
    }

    @Test
    void shouldRegisterCachingHealthCheck() {
        var registry = mock(HealthCheckRegistry.class);
        var cachingHealthCheck = CachingHealthCheck.register(registry, "happy", happyHealthCheck);

        assertAll(
            () -> assertThat(cachingHealthCheck.name()).isEqualTo("happy"),
            () -> assertThat(cachingHealthCheck.healthCheck()).isSameAs(happyHealthCheck),
            () -> assertThat(cachingHealthCheck.cacheExpiration())
                    .isEqualTo(CachingHealthCheck.DEFAULT_CACHE_EXPIRATION_DURATION)
        );

        verify(registry, only()).register("happy", cachingHealthCheck);
    }

    @Test
    void shouldReturnPreviousResult_WhenCachedResultHasNotExpired() {
        var expiration = Duration.ofSeconds(30);
        var cachingHealthCheck = new CachingHealthCheck("happy",
                happyHealthCheck,
                expiration,
                kiwiEnvironment);

        var firstCheckTime = Instant.now();
        var secondCheckTime = firstCheckTime.plusSeconds(12);
        var thirdCheckTime = firstCheckTime.plusSeconds(29);

        when(kiwiEnvironment.currentTimeMillis())
                .thenReturn(firstCheckTime.toEpochMilli())
                .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during creation of TimestampedResult
                .thenReturn(secondCheckTime.toEpochMilli())
                .thenReturn(thirdCheckTime.toEpochMilli());

        var expectedMessage = "Everything is awesome! Count: 1";

        // First check should do the real check
        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage(expectedMessage);

        // Second and third checks should return the same (cached) result
        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage(expectedMessage);

        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage(expectedMessage);

        verify(kiwiEnvironment, times(4)).currentTimeMillis();
        verifyNoMoreInteractions(kiwiEnvironment);
    }

    @Test
    void shouldExecuteRealCheck_WhenPastCacheExpiration() {
        var expiration = Duration.ofSeconds(30);
        var cachingHealthCheck = new CachingHealthCheck("happy",
                happyHealthCheck,
                expiration,
                kiwiEnvironment);

        var firstCheckTime = Instant.now();
        var secondCheckTime = firstCheckTime.plusSeconds(15);
        var thirdCheckTime = firstCheckTime.plusSeconds(31);

        when(kiwiEnvironment.currentTimeMillis())
                .thenReturn(firstCheckTime.toEpochMilli())
                .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during creation of TimestampedResult
                .thenReturn(secondCheckTime.toEpochMilli())
                .thenReturn(thirdCheckTime.toEpochMilli())
                .thenReturn(thirdCheckTime.toEpochMilli() + smallDelay());  // during creation of TimestampedResult

        var expectedMessage = "Everything is awesome! Count: 1";

        // First check should do the real check
        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage(expectedMessage);

        // Second check should return the same (cached) result
        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage(expectedMessage);

        // Third check should do the real check (after cache expiration)
        assertThatHealthCheck(cachingHealthCheck)
                .isHealthy()
                .hasMessage("Everything is awesome! Count: 2");

        verify(kiwiEnvironment, times(5)).currentTimeMillis();
        verifyNoMoreInteractions(kiwiEnvironment);
    }

    @Test
    void shouldNotLetExceptionsEscapeFromOriginalHealthCheck() {
        var badHealthCheck = new BadHealthCheck();
        var expiration = Duration.ofMinutes(10);
        var cachingHealthCheck = new CachingHealthCheck("bad",
                badHealthCheck,
                expiration,
                kiwiEnvironment);

        var firstCheckTime = Instant.now();
        var secondCheckTime = firstCheckTime.plus(5, ChronoUnit.MINUTES);

        when(kiwiEnvironment.currentTimeMillis())
                .thenReturn(firstCheckTime.toEpochMilli())
                .thenReturn(firstCheckTime.toEpochMilli() + smallDelay())  // during creation of TimestampedResult
                .thenReturn(secondCheckTime.toEpochMilli());

        var expectedMessage =
                f("'bad' - {}#execute unexpectedly threw an exception." +
                        " Exception: java.lang.RuntimeException, Message: Oops. Count: 1",
                        BadHealthCheck.class.getName());

        // First check should do the real check
        assertThatHealthCheck(cachingHealthCheck)
                .isUnhealthy()
                .hasMessage(expectedMessage);

        // Second check should return the same (cached) result
        assertThatHealthCheck(cachingHealthCheck)
                .isUnhealthy()
                .hasMessage(expectedMessage);

        verify(kiwiEnvironment, times(3)).currentTimeMillis();
        verifyNoMoreInteractions(kiwiEnvironment);
    }

    private static long smallDelay() {
        return ThreadLocalRandom.current().nextLong(5, 10);
    }

    static class HappyHealthCheck extends HealthCheck {
        final AtomicInteger count;

        HappyHealthCheck() {
            count = new AtomicInteger();
        }

        @Override
        protected Result check() {
            return Result.healthy("Everything is awesome! Count: " + count.incrementAndGet());
        }
    }

    static class BadHealthCheck extends HealthCheck {
        final AtomicInteger count;

        BadHealthCheck() {
            count = new AtomicInteger();
        }

        @Override
        protected Result check() {
            throw new RuntimeException("Oops. Count: " + count.incrementAndGet());
        }

        // Overrides default #execute method to throw an exception.
        // This should not happen in practice, since health checks
        // should not (usually) need to override the #execute method.
        @Override
        public Result execute() {
            return check();
        }
    }
}
