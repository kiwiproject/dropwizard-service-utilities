package org.kiwiproject.dropwizard.util.health.keystore;

import static java.util.Objects.nonNull;
import static org.kiwiproject.collect.KiwiLists.isNotNullOrEmpty;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResultBuilder;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResultBuilder;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.KiwiDropwizardDurations;
import org.kiwiproject.metrics.health.HealthStatus;

import java.time.ZoneId;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;

@Getter
@Builder
class KeystoreHealthResults {

    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");
    private static final KiwiEnvironment DEFAULT_KIWI_ENVIRONMENT = new DefaultEnvironment();

    @NonNull
    private final String path;

    @NonNull
    private final Duration expirationWarningThreshold;

    @NonNull
    private final List<BasicCertInfo> validCerts;

    @NonNull
    private final List<BasicCertInfo> expiredCerts;

    @NonNull
    private final List<BasicCertInfo> expiringCerts;

    private final Exception exception;

    @Builder.Default
    @NonNull
    private final KiwiEnvironment kiwiEnvironment = DEFAULT_KIWI_ENVIRONMENT;

    HealthCheck.ResultBuilder toResultBuilder() {
        return resultBuilderFor();
    }

    private HealthCheck.ResultBuilder resultBuilderFor() {
        if (nonNull(exception)) {
            return newUnhealthyResultBuilder(exception)
                    .withMessage("%s: exception", path);
        }

        if (isNotNullOrEmpty(expiredCerts)) {
            return newUnhealthyResultBuilder(HealthStatus.CRITICAL)
                    .withMessage("%s: %d expired certs, %d certs expiring soon",
                            path, expiredCerts.size(), expiringCerts.size());
        }

        if (isNotNullOrEmpty(expiringCerts)) {
            var severity = determineExpiringCertSeverity();

            return newUnhealthyResultBuilder(severity)
                    .withMessage("%s: %d certs expiring soon", path, expiringCerts.size());
        }

        return newHealthyResultBuilder().withMessage(path);
    }

    private HealthStatus determineExpiringCertSeverity() {
        var earliestExpirationDate = expiringCerts.stream()
                .map(BasicCertInfo::getExpiresOnUTC)
                .min(ChronoZonedDateTime::compareTo)
                .orElseThrow();

        var nowAtUTC = kiwiEnvironment.currentZonedDateTimeUTC();
        var durationUntilExpiration = java.time.Duration.between(nowAtUTC, earliestExpirationDate);
        var fullWarningThreshold = KiwiDropwizardDurations.fromDropwizardDuration(expirationWarningThreshold);

        var halfWarningThreshold = fullWarningThreshold.dividedBy(2);
        if (durationUntilExpiration.compareTo(halfWarningThreshold) >= 0) {
            return HealthStatus.INFO;
        }

        var quarterWarningThreshold = fullWarningThreshold.dividedBy(4);
        if (durationUntilExpiration.compareTo(quarterWarningThreshold) >= 0) {
            return HealthStatus.WARN;
        }

        return HealthStatus.CRITICAL;
    }
}
