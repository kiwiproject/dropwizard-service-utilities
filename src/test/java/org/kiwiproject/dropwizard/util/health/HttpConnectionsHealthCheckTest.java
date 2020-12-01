package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck.ClientConnectionInfo;
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

    @Nested
    class Construct {

        @Test
        void shouldCheckForInvalidThreshold(SoftAssertions softly) {
            softly.assertThatThrownBy(() -> newHealthCheckWithThreshold(0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("warningThreshold must be more than 0 and less than 100");

            softly.assertThatCode(() -> newHealthCheckWithThreshold(1.0)).doesNotThrowAnyException();
            softly.assertThatCode(() -> newHealthCheckWithThreshold(99.0)).doesNotThrowAnyException();

            softly.assertThatThrownBy(() -> newHealthCheckWithThreshold(100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("warningThreshold must be more than 0 and less than 100");
        }

        private void newHealthCheckWithThreshold(double value) {
            new HttpConnectionsHealthCheck(metrics, value);
        }
    }

    @Nested
    class Check {

        @Nested
        class IsHealthy {

            @Test
            void whenNoDropwizardJerseyClients() {
                when(metrics.getGauges(any())).thenReturn(new TreeMap<>());

                assertThatHealthCheck(healthCheck)
                        .isHealthy()
                        .hasMessage("No HTTP clients found with metrics");
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Test
            void whenOneDropwizardJerseyClient_ThatIsBelowDefaultWarningThreshold() {
                SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                        leasedGaugeNameFor("some-client"), gaugeReturning(4),
                        maxGaugeNameFor("some-client"), gaugeReturning(10)
                );

                when(metrics.getGauges(any())).thenReturn(gauges);

                assertThatHealthCheck(healthCheck)
                        .isHealthy()
                        .hasMessage("1 HTTP client(s) < 50.0% leased connections.");

                // NOTE: Yes this is calling the check method a second time, but I need the details map for
                // analysis and verification. Once we had more options to the HealthCheckResultAssertions#hasDetail
                // methods, we can remove this part.

                var result = healthCheck.check();

                var expectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("some-client")
                        .leased(4)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                assertThat(result.getDetails()).containsKey("healthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("healthyClients"))
                        .contains(entry("some-client", expectedClientInfo));

                assertThat(result.getDetails()).containsKey("unhealthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("unhealthyClients")).isEmpty();

            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Test
            void whenMultipleDropwizardJerseyClients_ThatAreAllBelowDefaultWarningThreshold() {
                SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                        leasedGaugeNameFor("some-client"), gaugeReturning(4),
                        maxGaugeNameFor("some-client"), gaugeReturning(10),
                        leasedGaugeNameFor("another-client"), gaugeReturning(2),
                        maxGaugeNameFor("another-client"), gaugeReturning(10)
                );

                when(metrics.getGauges(any())).thenReturn(gauges);

                assertThatHealthCheck(healthCheck)
                        .isHealthy()
                        .hasMessage("2 HTTP client(s) < 50.0% leased connections.");

                // NOTE: Yes this is calling the check method a second time, but I need the details map for
                // analysis and verification. Once we had more options to the HealthCheckResultAssertions#hasDetail
                // methods, we can remove this part.

                var result = healthCheck.check();

                var someClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("some-client")
                        .leased(4)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                var anotherClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("another-client")
                        .leased(2)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                assertThat(result.getDetails()).containsKey("healthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("healthyClients"))
                        .contains(
                                entry("some-client", someClientExpectedClientInfo),
                                entry("another-client", anotherClientExpectedClientInfo)
                        );

                assertThat(result.getDetails()).containsKey("unhealthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("unhealthyClients")).isEmpty();
            }
        }

        @Nested
        class IsUnhealthy {

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Test
            void whenMultipleDropwizardJerseyClients_ThatOneIsAboveDefaultWarningThreshold() {
                SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                        leasedGaugeNameFor("some-client"), gaugeReturning(5),
                        maxGaugeNameFor("some-client"), gaugeReturning(10),
                        leasedGaugeNameFor("another-client"), gaugeReturning(2),
                        maxGaugeNameFor("another-client"), gaugeReturning(10)
                );

                when(metrics.getGauges(any())).thenReturn(gauges);

                assertThatHealthCheck(healthCheck)
                        .isUnhealthy()
                        .hasMessage("1 of 2 HTTP client(s) >= 50.0% leased connections.");

                // NOTE: Yes this is calling the check method a second time, but I need the details map for
                // analysis and verification. Once we had more options to the HealthCheckResultAssertions#hasDetail
                // methods, we can remove this part.

                var result = healthCheck.check();

                var someClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("some-client")
                        .leased(5)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                var anotherClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("another-client")
                        .leased(2)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                assertThat(result.getDetails()).containsKey("healthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("healthyClients"))
                        .contains(entry("another-client", anotherClientExpectedClientInfo));

                assertThat(result.getDetails()).containsKey("unhealthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("unhealthyClients"))
                        .contains(entry("some-client", someClientExpectedClientInfo));
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Test
            void whenMultipleDropwizardJerseyClients_ThatAllAreAboveDefaultWarningThreshold() {
                SortedMap<String, Gauge> gauges = (TreeMap) KiwiMaps.newTreeMap(
                        leasedGaugeNameFor("some-client"), gaugeReturning(5),
                        maxGaugeNameFor("some-client"), gaugeReturning(10),
                        leasedGaugeNameFor("another-client"), gaugeReturning(9),
                        maxGaugeNameFor("another-client"), gaugeReturning(10)
                );

                when(metrics.getGauges(any())).thenReturn(gauges);

                assertThatHealthCheck(healthCheck)
                        .isUnhealthy()
                        .hasMessage("2 of 2 HTTP client(s) >= 50.0% leased connections.");

                // NOTE: Yes this is calling the check method a second time, but I need the details map for
                // analysis and verification. Once we had more options to the HealthCheckResultAssertions#hasDetail
                // methods, we can remove this part.

                var result = healthCheck.check();

                var someClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("some-client")
                        .leased(5)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                var anotherClientExpectedClientInfo = ClientConnectionInfo.builder()
                        .clientName("another-client")
                        .leased(9)
                        .max(10)
                        .warningThreshold(50.0)
                        .build();

                assertThat(result.getDetails()).containsKey("healthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("healthyClients")).isEmpty();

                assertThat(result.getDetails()).containsKey("unhealthyClients");
                assertThat((Map<String, ClientConnectionInfo>)result.getDetails().get("unhealthyClients"))
                        .contains(
                                entry("some-client", someClientExpectedClientInfo),
                                entry("another-client", anotherClientExpectedClientInfo)
                        );
            }
        }
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
