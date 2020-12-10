package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.dropwizard.util.health.ServerErrorHealthCheck.DEFAULT_CRITICAL_THRESHOLD;
import static org.kiwiproject.dropwizard.util.health.ServerErrorHealthCheck.DEFAULT_WARNING_THRESHOLD;
import static org.kiwiproject.dropwizard.util.health.ServerErrorHealthCheck.FIFTEEN_MINUTES_IN_SECONDS;
import static org.kiwiproject.dropwizard.util.health.ServerErrorHealthCheck.METER_NAME;
import static org.kiwiproject.dropwizard.util.health.ServerErrorHealthCheck.METRIC_FILTER;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.metrics.health.HealthStatus;

import java.util.TreeMap;

@DisplayName("ServerErrorHealthCheck")
class ServerErrorHealthCheckTest {

    private MetricRegistry metrics;
    private ServerErrorHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        metrics = mock(MetricRegistry.class);
        healthCheck = new ServerErrorHealthCheck(metrics);
    }

    @Nested
    class IsHealthy {

        @Test
        void whenMetersReturnedIsNull() {
            when(metrics.getMeters(METRIC_FILTER)).thenReturn(null);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("{} meter is not configured", METER_NAME);
        }

        @Test
        void whenMetersReturnedIsEmpty() {
            when(metrics.getMeters(METRIC_FILTER)).thenReturn(new TreeMap<>());

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("{} meter is not configured", METER_NAME);
        }

        @Test
        void whenMetersReturnedDoesNotHaveFilter() {
            var meters = new TreeMap<String, Meter>();
            meters.put("some-meter-name", mock(Meter.class));

            when(metrics.getMeters(METRIC_FILTER)).thenReturn(meters);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("{} meter is not configured", METER_NAME);
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.5, 0.999999})
        void whenRequiredMeterIsReturnedAndRateIsLessThanThreshold(double count) {
            var meter = mock(Meter.class);
            when(meter.getFifteenMinuteRate()).thenReturn(count / FIFTEEN_MINUTES_IN_SECONDS);

            var meters = new TreeMap<String, Meter>();
            meters.put(METER_NAME, meter);

            when(metrics.getMeters(METRIC_FILTER)).thenReturn(meters);

            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("No 5xx error responses in the last 15 minutes")
                    .hasDetail("rate", count / FIFTEEN_MINUTES_IN_SECONDS)
                    .hasDetail("approximateCount", count)
                    .hasDetail("warningThreshold", DEFAULT_WARNING_THRESHOLD)
                    .hasDetail("criticalThreshold", DEFAULT_CRITICAL_THRESHOLD)
                    .hasDetail("meter", METER_NAME);
        }
    }

    @Nested
    class IsUnhealthy {

        @ParameterizedTest
        @ValueSource(doubles = {1.0, 5.0, 9.999999})
        void withWarningSeverity_WhenRateIsEqualToOrGreaterThanWarningThreshold(double count) {
            var meter = mock(Meter.class);
            when(meter.getFifteenMinuteRate()).thenReturn(count / FIFTEEN_MINUTES_IN_SECONDS);

            var meters = new TreeMap<String, Meter>();
            meters.put(METER_NAME, meter);

            when(metrics.getMeters(METRIC_FILTER)).thenReturn(meters);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("severity", HealthStatus.WARN.name())
                    .hasMessage("Some 5xx error responses in the last 15 minutes")
                    .hasDetail("rate", count / FIFTEEN_MINUTES_IN_SECONDS)
                    .hasDetail("approximateCount", count)
                    .hasDetail("warningThreshold", DEFAULT_WARNING_THRESHOLD)
                    .hasDetail("criticalThreshold", DEFAULT_CRITICAL_THRESHOLD)
                    .hasDetail("meter", METER_NAME);
        }

        @ParameterizedTest
        @ValueSource(doubles = {10.0, 100.0})
        void withCriticalSeverity_WhenRateIsEqualToOrGreaterThanCriticalThreshold(double count) {
            var meter = mock(Meter.class);
            when(meter.getFifteenMinuteRate()).thenReturn(count / FIFTEEN_MINUTES_IN_SECONDS);

            var meters = new TreeMap<String, Meter>();
            meters.put(METER_NAME, meter);

            when(metrics.getMeters(METRIC_FILTER)).thenReturn(meters);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .hasMessage("Critical level of 5xx error responses in the last 15 minutes")
                    .hasDetail("rate", count / FIFTEEN_MINUTES_IN_SECONDS)
                    .hasDetail("approximateCount", count)
                    .hasDetail("warningThreshold", DEFAULT_WARNING_THRESHOLD)
                    .hasDetail("criticalThreshold", DEFAULT_CRITICAL_THRESHOLD)
                    .hasDetail("meter", METER_NAME);
        }

        @Test
        void withCustomThresholdsMet() {
            healthCheck = new ServerErrorHealthCheck(metrics, 5, 20);

            var warningMeter = mock(Meter.class);
            when(warningMeter.getFifteenMinuteRate()).thenReturn(5.5 / FIFTEEN_MINUTES_IN_SECONDS);

            var warningMeters = new TreeMap<String, Meter>();
            warningMeters.put(METER_NAME, warningMeter);

            var criticalMeter = mock(Meter.class);
            when(criticalMeter.getFifteenMinuteRate()).thenReturn(21.0 / FIFTEEN_MINUTES_IN_SECONDS);

            var criticalMeters = new TreeMap<String, Meter>();
            criticalMeters.put(METER_NAME, criticalMeter);

            when(metrics.getMeters(METRIC_FILTER))
                    .thenReturn(warningMeters)
                    .thenReturn(criticalMeters);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("severity", HealthStatus.WARN.name())
                    .hasMessage("Some 5xx error responses in the last 15 minutes")
                    .hasDetail("rate", 5.5 / FIFTEEN_MINUTES_IN_SECONDS)
                    .hasDetail("approximateCount", 5.5)
                    .hasDetail("warningThreshold", 5)
                    .hasDetail("criticalThreshold", 20)
                    .hasDetail("meter", METER_NAME);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("severity", HealthStatus.CRITICAL.name())
                    .hasMessage("Critical level of 5xx error responses in the last 15 minutes")
                    .hasDetail("rate", 21.0 / FIFTEEN_MINUTES_IN_SECONDS)
                    .hasDetail("approximateCount", 21.0)
                    .hasDetail("warningThreshold", 5)
                    .hasDetail("criticalThreshold", 20)
                    .hasDetail("meter", METER_NAME);
        }
    }
}
