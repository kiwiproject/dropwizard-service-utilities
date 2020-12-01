package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.core.type.TypeReference;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@DisplayName("HttpConnectionsHealthCheck")
@ExtendWith(SoftAssertionsExtension.class)
class HttpConnectionsHealthCheckTest {

    private HttpConnectionsHealthCheck healthCheck;
    private MetricRegistry metrics;

    @BeforeEach
    void setUp() {
        var environment = DropwizardMockitoMocks.mockEnvironment();
        metrics = DropwizardMockitoMocks.mockMetricRegistry(environment);
        healthCheck = new HttpConnectionsHealthCheck(metrics);
    }

    @Test
    void testClientIsNotBeingDumb_ByCheckingForInvalidThreshold(SoftAssertions softly) {
        softly.assertThatThrownBy(() -> newHealthCheckWithThreshold(0.0)).isExactlyInstanceOf(IllegalArgumentException.class);
        softly.assertThat(catchThrowable(() -> newHealthCheckWithThreshold(1.0))).isNull();
        softly.assertThat(catchThrowable(() -> newHealthCheckWithThreshold(99.0))).isNull();
        softly.assertThatThrownBy(() -> newHealthCheckWithThreshold(100.0)).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("UnusedReturnValue")
    private HttpConnectionsHealthCheck newHealthCheckWithThreshold(double value) {
        return new HttpConnectionsHealthCheck(metrics, value);
    }

    @Test
    void testCheck_WhenNoDropwizardJerseyClients(SoftAssertions softly) {
        when(metrics.getGauges(any())).thenReturn(new TreeMap<>());

        HealthCheck.Result result = healthCheck.check();

        softly.assertThat(result.isHealthy()).isTrue();
        softly.assertThat(result.getMessage()).isEqualTo("No HTTP clients found with metrics");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testCheck_OneDropwizardJerseyClient_ThatIsBelowDefaultWarningThreshold(SoftAssertions softly) {
        SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                leasedGaugeNameFor("some-client"), gaugeReturning(4),
                maxGaugeNameFor("some-client"), gaugeReturning(10)
        );

        when(metrics.getGauges(any())).thenReturn(gauges);

        HealthCheck.Result result = healthCheck.check();

        softly.assertThat(result.isHealthy()).isTrue();
        softly.assertThat(result.getMessage()).isEqualTo("1 HTTP client(s) < 50.0% leased connections.");

        verifyClientInfo(softly, result, "details.healthyClients.some-client", "some-client", 4);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testCheck_MultipleDropwizardJerseyClients_WhenAllAreBelowDefaultWarningThreshold(SoftAssertions softly) {
        SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                leasedGaugeNameFor("some-client"), gaugeReturning(4),
                maxGaugeNameFor("some-client"), gaugeReturning(10),
                leasedGaugeNameFor("another-client"), gaugeReturning(2),
                maxGaugeNameFor("another-client"), gaugeReturning(10)
        );

        when(metrics.getGauges(any())).thenReturn(gauges);

        HealthCheck.Result result = healthCheck.check();

        softly.assertThat(result.isHealthy()).isTrue();
        softly.assertThat(result.getMessage()).isEqualTo("2 HTTP client(s) < 50.0% leased connections.");

        verifyClientInfo(softly, result, "details.healthyClients.some-client", "some-client", 4);
        verifyClientInfo(softly, result, "details.healthyClients.another-client", "another-client", 2);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testCheck_MultipleDropwizardJerseyClients_WhenOneIsAboveDefaultWarningThreshold(SoftAssertions softly) {
        SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                leasedGaugeNameFor("some-client"), gaugeReturning(5),
                maxGaugeNameFor("some-client"), gaugeReturning(10),
                leasedGaugeNameFor("another-client"), gaugeReturning(2),
                maxGaugeNameFor("another-client"), gaugeReturning(10)
        );

        when(metrics.getGauges(any())).thenReturn(gauges);

        HealthCheck.Result result = healthCheck.check();

        softly.assertThat(result.isHealthy()).isFalse();
        softly.assertThat(result.getMessage()).isEqualTo("1 of 2 HTTP client(s) >= 50.0% leased connections.");

        verifyClientInfo(softly, result, "details.unhealthyClients.some-client", "some-client", 5);
        verifyClientInfo(softly, result, "details.healthyClients.another-client", "another-client", 2);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void testCheck_MultipleDropwizardJerseyClients_WhenAllAreAboveDefaultWarningThreshold(SoftAssertions softly) {
        SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                leasedGaugeNameFor("some-client"), gaugeReturning(5),
                maxGaugeNameFor("some-client"), gaugeReturning(10),
                leasedGaugeNameFor("another-client"), gaugeReturning(9),
                maxGaugeNameFor("another-client"), gaugeReturning(10)
        );

        when(metrics.getGauges(any())).thenReturn(gauges);

        HealthCheck.Result result = healthCheck.check();

        softly.assertThat(result.isHealthy()).isFalse();
        softly.assertThat(result.getMessage())
                .isEqualTo("2 of 2 HTTP client(s) >= 50.0% leased connections.");
    }

    @SuppressWarnings("unchecked")
    private void verifyClientInfo(SoftAssertions softly, HealthCheck.Result result, String path, String clientName, int leasedCount) {
        var client = JSON_HELPER.getPath(result, path, new TypeReference<Map<String, Object>>() {});

        softly.assertThat(client).contains(
                entry("clientName", clientName),
                entry("leased", leasedCount),
                entry("max", 10)
        );
    }

    private Gauge<Integer> gaugeReturning(Integer value) {
        return () -> value;
    }

    private String leasedGaugeNameFor(String clientName) {
        return "org.apache.http.conn.HttpClientConnectionManager." + clientName + ".leased-connections";
    }

    private String maxGaugeNameFor(String clientName) {
        return "org.apache.http.conn.HttpClientConnectionManager." + clientName + ".max-connections";
    }
}
