package org.kiwiproject.dropwizard.util.health.keystore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.collect.KiwiLists.first;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.util.Duration;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.util.config.KeystoreConfig;
import org.kiwiproject.security.KeyStoreType;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.List;
import java.util.Map;

@DisplayName("ExpiringKeystoreHealthCheck")
@ExtendWith(SoftAssertionsExtension.class)
class ExpiringKeystoreHealthCheckTest {

    @Nested
    class ShouldReportUnhealthy {
        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreExpiredCerts(SoftAssertions softly) throws Exception {
            var path = ResourceHelpers.resourceFilePath("test-keystore-with-one-expired-cert.jks");
            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass(" password")
                    .ttl(Duration.days(45))
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isFalse();
            softly.assertThat(result.getMessage()).isEqualTo(path + ": 1 expired certs, 0 certs expiring soon");
            softly.assertThat(result.getError()).isNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("ttl", "45 days")
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).hasSize(1);

            var certInfo = first(expiredCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Unit Test, OU=Development, 0=Project, L=Here, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Unit Test, OU=Development, 0=Project, L=Here, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuedOn()).isEqualTo("Fri, 28 Aug 2015 21:11:50 GMT");
            softly.assertThat(certInfo.getExpiresOn()).isEqualTo("Thu, 26 Nov 2015 21:11:50 GMT");

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        void whenAnExceptionIsThrown(SoftAssertions softly) throws Exception {
            var path = ResourceHelpers.resourceFilePath("test-keystore-with-one-valid-cert-until-2029.pkcs12");
            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass(" not-the-password")
                    .type(KeyStoreType.PKCS12.name())
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isFalse();
            softly.assertThat(result.getMessage()).isEqualTo(path + ": exception");
            softly.assertThat(result.getError()).isNotNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("ttl", "30 days") // default ttl
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).isEmpty();

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).isEmpty();
        }
    }

    /**
     * The keytool command that generated the valid cert:
     * <p>
     * keytool -genkeypair -keyalg RSA -alias test -keystore test-keystore-with-one-valid-cert.jks -storepass password
     * -validity 3650 -keysize 2048
     * <p>
     * Issued on: Mon Jun 10 21:34:22 GMT 2019
     * <p>
     * Expires on: Thu Jun 07 21:34:22 GMT 2029
     * <p>
     * Then renamed it to: test-keystore-with-one-valid-cert-until-2029.pkcs12
     */
    @Nested
    class ShouldReportHealthy {
        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreOnlyValidCerts(SoftAssertions softly) throws Exception {
            var path = ResourceHelpers.resourceFilePath("test-keystore-with-one-valid-cert-until-2029.pkcs12");
            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("password")
                    .type(KeyStoreType.PKCS12.name())
                    .ttl(Duration.days(20))
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isTrue();
            softly.assertThat(result.getMessage()).isEqualTo(path);
            softly.assertThat(result.getError()).isNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("ttl", "20 days")
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).hasSize(1);

            var certInfo = first(validCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Valid Test, OU=Development, 0=Project, L=There, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Valid Test, OU=Development, 0=Project, L=There, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuedOn()).isEqualTo("Mon, 10 Jun 2019 21:34:22 GMT");
            softly.assertThat(certInfo.getExpiresOn()).isEqualTo("Thu, 7 Jun 2029 21:34:22 GMT");

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).isEmpty();

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).isEmpty();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BasicCertInfo> getBasicCertInfoList(Map<String, Object> details, String key) {
        return (List<BasicCertInfo>) details.get(key);
    }

    @Nested
    class ToCertificate {
        @Test
        void shouldThrowException_WhenKeyStoreNotLoaded() throws KeyStoreException {
            var keyStore = KeyStore.getInstance(KeyStoreType.PKCS12.name());
            var path = "/path/to/cert.pkc512";

            assertThatThrownBy(() -> ExpiringKeystoreHealthCheck.toCertificate("anAlias", path, keyStore))
                    .isExactlyInstanceOf(ExpiringKeystoreHealthCheck.KeyStoreNotLoadedException.class)
                    .hasMessage("Keystore at path %s not loaded", path);
        }
    }
}