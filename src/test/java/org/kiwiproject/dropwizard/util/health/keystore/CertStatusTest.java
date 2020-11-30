package org.kiwiproject.dropwizard.util.health.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.time.KiwiInstants;

import java.time.Instant;

@DisplayName("CertStatus")
class CertStatusTest {

    @Nested
    class DetermineCertStatus {
        @Test
        void shouldReturnExpired_WhenAfterExpirationDate() {
            var now = Instant.now();
            var expirationThreshold = KiwiInstants.plusDays(now, 30);
            var notAfter = KiwiInstants.minusDays(now, 1);
            assertThat(CertStatus.determineCertStatus(now, expirationThreshold, notAfter))
                    .isEqualTo(CertStatus.EXPIRED);
        }

        @Test
        void shouldReturnExpiringSoon_WhenWithinExpirationThreshold() {
            var now = Instant.now();
            var expirationThreshold = KiwiInstants.plusDays(now, 20);
            var notAfter = KiwiInstants.plusDays(now, 19);
            assertThat(CertStatus.determineCertStatus(now, expirationThreshold, notAfter))
                    .isEqualTo(CertStatus.EXPIRING_SOON);
        }

        @Test
        void shouldReturnValid_WhenBeforeExpirationDate_AndNotWithinExpirationThreshold() {
            var now = Instant.now();
            var expirationThreshold = KiwiInstants.plusDays(now, 30);
            var notAfter = KiwiInstants.plusDays(now, 31);
            assertThat(CertStatus.determineCertStatus(now, expirationThreshold, notAfter))
                    .isEqualTo(CertStatus.VALID);
        }
    }
}
