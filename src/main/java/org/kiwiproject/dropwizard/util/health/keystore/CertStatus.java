package org.kiwiproject.dropwizard.util.health.keystore;

import java.time.Instant;

enum CertStatus {

    VALID, EXPIRED, EXPIRING_SOON;

    static CertStatus determineCertStatus(Instant now, Instant expirationThreshold, Instant notAfter) {
        if (now.isAfter(notAfter)) {
            return EXPIRED;
        }

        if (expirationThreshold.isAfter(notAfter)) {
            return EXPIRING_SOON;
        }

        return VALID;
    }
}
