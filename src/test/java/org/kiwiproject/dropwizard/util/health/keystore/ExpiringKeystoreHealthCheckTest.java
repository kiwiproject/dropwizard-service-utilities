package org.kiwiproject.dropwizard.util.health.keystore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.collect.KiwiLists.first;

import io.dropwizard.util.Duration;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.kiwiproject.base.process.ProcessHelper;
import org.kiwiproject.dropwizard.util.config.KeystoreConfig;
import org.kiwiproject.io.KiwiIO;
import org.kiwiproject.security.KeyStoreType;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@DisplayName("ExpiringKeystoreHealthCheck")
@ExtendWith(SoftAssertionsExtension.class)
class ExpiringKeystoreHealthCheckTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EE, d LLL yyyy");

    @Nested
    class ShouldReportUnhealthy {
        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreExpiredCerts(SoftAssertions softly, @TempDir Path tempDir) throws Exception {
            var dn = "CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US";
            var path = createTemporaryKeystore(tempDir, 30, dn);

            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("unittest")
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
                    entry("ttl", Duration.days(45))
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).hasSize(1);

            var certInfo = first(expiredCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");

            var sixtyDaysAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(60);
            var sixtyDaysAgoString = DATE_FORMATTER.format(sixtyDaysAgo);
            softly.assertThat(certInfo.getIssuedOn()).startsWith(sixtyDaysAgoString);

            var expirationDate = sixtyDaysAgo.plusDays(30);
            var expirationDateString = DATE_FORMATTER.format(expirationDate);
            softly.assertThat(certInfo.getExpiresOn()).startsWith(expirationDateString);

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreExpiringCerts(SoftAssertions softly, @TempDir Path tempDir) throws Exception {
            var dn = "CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US";
            var path = createTemporaryKeystore(tempDir, 75, dn);

            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("unittest")
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isFalse();
            softly.assertThat(result.getMessage()).isEqualTo(path + ": 1 certs expiring soon");
            softly.assertThat(result.getError()).isNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("ttl", Duration.days(30))
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).isEmpty();

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).hasSize(1);

            var certInfo = first(expiringCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");

            var sixtyDaysAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(60);
            var sixtyDaysAgoString = DATE_FORMATTER.format(sixtyDaysAgo);
            softly.assertThat(certInfo.getIssuedOn()).startsWith(sixtyDaysAgoString);

            var expirationDate = sixtyDaysAgo.plusDays(75);
            var expirationDateString = DATE_FORMATTER.format(expirationDate);
            softly.assertThat(certInfo.getExpiresOn()).startsWith(expirationDateString);

        }

        @SuppressWarnings("unchecked")
        @Test
        void whenAnExceptionIsThrown(SoftAssertions softly, @TempDir Path tempDir) throws Exception {
            var dn = "CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US";
            var path = createTemporaryKeystore(tempDir, 75, dn);

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
                    entry("ttl", Duration.days(30)) // default ttl
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
        void whenThereAreOnlyValidCerts(SoftAssertions softly, @TempDir Path tempDir) throws Exception {
            var dn = "CN=Valid Test,OU=Development,O=Project,L=There,ST=VA,C=US";
            var path = createTemporaryKeystore(tempDir, 600, dn);

            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("unittest")
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
                    entry("ttl", Duration.days(20))
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).hasSize(1);

            var certInfo = first(validCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Valid Test, OU=Development, O=Project, L=There, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Valid Test, OU=Development, O=Project, L=There, ST=VA, C=US");

            var sixtyDaysAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(60);
            var sixtyDaysAgoString = DATE_FORMATTER.format(sixtyDaysAgo);
            softly.assertThat(certInfo.getIssuedOn()).startsWith(sixtyDaysAgoString);

            var expirationDate = sixtyDaysAgo.plusDays(600);
            var expirationDateString = DATE_FORMATTER.format(expirationDate);
            softly.assertThat(certInfo.getExpiresOn()).startsWith(expirationDateString);

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

    private String createTemporaryKeystore(Path dir, int daysValid, String dn) throws InterruptedException {
        var keystorePath = dir.resolve("temporary-keystore.jks").toAbsolutePath().toString();

        var processHelper = new ProcessHelper();
        var keystoreGenProcess = processHelper.launch("keytool", "-alias", "Test", "-genkey", "-keystore", keystorePath, "-keyalg", "RSA", "-validity", String.valueOf(daysValid), "-startdate", "-60d", "-storepass", "unittest", "-keypass", "unittest", "-dname", dn);

        processHelper.waitForExit(keystoreGenProcess, 500, TimeUnit.MILLISECONDS);

        System.out.println(KiwiIO.readInputStreamOf(keystoreGenProcess));
        System.out.println(KiwiIO.readErrorStreamOf(keystoreGenProcess));

        return keystorePath;
    }
}