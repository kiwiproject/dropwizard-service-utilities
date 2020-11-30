package org.kiwiproject.dropwizard.util.health.keystore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
class BasicCertInfo {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    private final String subjectDN;
    private final String issuerDN;
    private final String issuedOn;
    private final String expiresOn;

    @JsonIgnore
    private final ZonedDateTime expiresOnUTC;

    @JsonIgnore
    private final CertStatus certStatus;

    public static BasicCertInfo from(X509Certificate x509Cert, Instant now, Instant expirationThreshold) {
        var notAfter = x509Cert.getNotAfter().toInstant();
        var certStatus = CertStatus.determineCertStatus(now, expirationThreshold, notAfter);

        return builder()
                .subjectDN(x509Cert.getSubjectDN().getName())
                .issuedOn(rfc1123FormatAtUTC(x509Cert.getNotBefore().toInstant()))
                .expiresOn(rfc1123FormatAtUTC(notAfter))
                .expiresOnUTC(notAfter.atZone(UTC_ZONE_ID))
                .issuerDN(x509Cert.getIssuerDN().getName())
                .certStatus(certStatus)
                .build();
    }

    private static String rfc1123FormatAtUTC(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atZone(UTC_ZONE_ID));
    }
}
