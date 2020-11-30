package org.kiwiproject.dropwizard.util.health.keystore;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.util.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.metrics.health.HealthCheckResults;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@DisplayName("KeystoreHealthResults")
class KeystoreHealthResultsTest {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    @Nested
    class WhenNoExpiredOrExpiringCerts {
        @Test
        void shouldReportHealthy() {
            var results = KeystoreHealthResults.builder()
                    .path("/some/path/to/keystore.jks")
                    .expirationWarningThreshold(Duration.days(30))
                    .validCerts(List.of(BasicCertInfo.builder().subjectDN("valid-cert-1").build()))
                    .expiredCerts(List.of())
                    .expiringCerts(List.of())
                    .build();
            assertThat(results.toResultBuilder().build())
                    .isHealthy()
                    .hasMessage("/some/path/to/keystore.jks");
        }
    }

    @Nested
    class WhenThereIsAnException {
        private KeystoreHealthResults results;

        @BeforeEach
        void setUp() {
            results = KeystoreHealthResults.builder()
                    .path("/a/path")
                    .expirationWarningThreshold(Duration.days(30))
                    .validCerts(List.of())
                    .expiredCerts(List.of())
                    .expiringCerts(List.of())
                    .exception(new IOException("oops"))
                    .build();
        }

        @Test
        void shouldBeUnhealthy() {
            assertThat(results.toResultBuilder().build())
                    .isUnhealthy()
                    .hasErrorExactlyInstanceOf(IOException.class)
                    .hasErrorWithMessage("oops");
        }

        @Test
        void shouldHaveCriticalSeverity() {
            assertThat(results.toResultBuilder().build())
                    .hasDetail(HealthCheckResults.SEVERITY_DETAIL, "CRITICAL");
        }
    }

    @Nested
    class WhenThereAreExpiredCerts {
        private KeystoreHealthResults results;

        @BeforeEach
        void setUp() {
            var expiredCertl = BasicCertInfo.builder()
                    .subjectDN("cert-1")
                    .build();
            var expiredCert2 = BasicCertInfo.builder()
                    .subjectDN("cert-2")
                    .build();
            results = KeystoreHealthResults.builder()
                    .path("/a/path/keystore.jks")
                    .expirationWarningThreshold(Duration.days(30))
                    .validCerts(List.of())
                    .expiredCerts(List.of(expiredCertl, expiredCert2))
                    .expiringCerts(List.of())
                    .build();
        }

        @Test
        void shouldBeUnhealthy() {
            assertThat(results.toResultBuilder().build())
                    .isUnhealthy()
                    .hasMessage("/a/path/keystore.jks: 2 expired certs, 0 certs expiring soon");
        }
    }

    @Nested
    class WhenThereAreExpiringCerts {
        private ZonedDateTime now;
        private KiwiEnvironment kiwiEnvironment;

        @BeforeEach
        void setUp() {
            now = ZonedDateTime.now(UTC_ZONE_ID);
            kiwiEnvironment = mock(KiwiEnvironment.class);
            when(kiwiEnvironment.currentZonedDateTimeUTC()).thenReturn(now);
        }

        @Test
        void shouldBeInfo_WhenExpiration_MoreThanHalf0fTimeToLive() {
            var expirationDate1 = now.plusDays(15).plusSeconds(1);
            var expirationDate2 = now.plusDays(25);
            buildResultAndAssertSeverity(30, "INFO", expirationDate1, expirationDate2);
        }

        @Test
        void shouldBeInfo_WhenExpiration_ExactlyHalf0fTimeToLive() {
            var expirationDate1 = now.plusDays(15);
            var expirationDate2 = now.plusDays(25);
            buildResultAndAssertSeverity(30, "INFO", expirationDate1, expirationDate2);
        }

        @Test
        void shouldBeWarn_WhenExpiration_SlightlyLessThanHalf0fTimeToLive() {
            var expirationDate1 = now.plusDays(10).minusSeconds(1);
            var expirationDate2 = now.plusDays(25);
            buildResultAndAssertSeverity(20, "WARN", expirationDate1, expirationDate2);
        }

        @Test
        void shouldBeWarn_WhenExpiration_SlightlyMoreThanOneQuarterOfTimeToLive() {
            var expirationDate1 = now.plusDays(5).plusSeconds(1);
            var expirationDate2 = now.plusDays(25);
            buildResultAndAssertSeverity(20, "WARN", expirationDate1, expirationDate2);
        }

        @Test
        void shouldBeWarn_WhenExpiration_ExactlyOneQuarterOfTimeToLive() {
            var expirationDate1 = now.plusDays(3);
            var expirationDate2 = now.plusDays(16);
            buildResultAndAssertSeverity(12, "WARN", expirationDate1, expirationDate2);
        }

        @Test
        void shouldBeCritical_WhenExpiration_SlightlyLessThanOneQuarterOfTimeToLive() {
            var expirationDate1 = now.plusDays(18);
            var expirationDate2 = now.plusDays(5).minusSeconds(1);
            buildResultAndAssertSeverity(20, "CRITICAL", expirationDate1, expirationDate2);
        }

        private void buildResultAndAssertSeverity(int ttlInDays,
                                                  String expectedSeverity,
                                                  ZonedDateTime expirationDate1,
                                                  ZonedDateTime expirationDate2) {
            var keystoreHealthResults = resultsWithOnlyExpiringCerts(ttlInDays, kiwiEnvironment, expirationDate1, expirationDate2);
            var healthCheckResult = keystoreHealthResults.toResultBuilder().build();
            assertThat(healthCheckResult)
                    .isUnhealthy()
                    .hasDetail(HealthCheckResults.SEVERITY_DETAIL, expectedSeverity);
        }

        private KeystoreHealthResults resultsWithOnlyExpiringCerts(int ttlInDays,
                                                                   KiwiEnvironment kiwiEnvironment,
                                                                   ZonedDateTime... expirationDates) {

            var expiringCerts = Arrays.stream(expirationDates)
                    .map(expirationDate -> BasicCertInfo.builder()
                            .expiresOnUTC(expirationDate)
                            .expiresOn(DateTimeFormatter.RFC_1123_DATE_TIME.format(expirationDate))
                            .build())
                    .collect(toUnmodifiableList());

            return KeystoreHealthResults.builder()
                    .kiwiEnvironment(kiwiEnvironment)
                    .path("/etc/pki/test.jks")
                    .expirationWarningThreshold(Duration.days(ttlInDays))
                    .validCerts(List.of())
                    .expiredCerts(List.of())
                    .expiringCerts(expiringCerts)
                    .build();
        }
    }
}
