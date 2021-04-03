package org.kiwiproject.dropwizard.util.health.keystore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.collect.KiwiLists.first;

import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
class ExpiringKeystoreHealthCheckTest {

    // This formats dates to look like "Fri, 19 Mar 2021" or "Sun, 7 Jun 2020"
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EE, d LLL yyyy");

    @Nested
    class ShouldReportUnhealthy {
        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreExpiredCerts(SoftAssertions softly, @TempDir Path tempDir) {
            var dn = "CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US";
            var path = createTemporaryKeystore(tempDir, 30, dn);

            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("unittest")
                    .expirationWarningThreshold(Duration.days(45))
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isFalse();
            softly.assertThat(result.getMessage()).isEqualTo(path + ": 1 expired certs, 0 certs expiring soon");
            softly.assertThat(result.getError()).isNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("expirationWarningThreshold", Duration.days(45))
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).hasSize(1);

            var certInfo = first(expiredCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Unit Test, OU=Development, O=Project, L=Here, ST=VA, C=US");

            var sixtyDaysAgo = ZonedDateTime.now().minusDays(60).toInstant().atZone(ZoneId.of("UTC"));
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
        void whenThereAreExpiringCerts(SoftAssertions softly, @TempDir Path tempDir) {
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
                    entry("expirationWarningThreshold", Duration.days(30))
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

            var sixtyDaysAgo = ZonedDateTime.now().minusDays(60).toInstant().atZone(ZoneId.of("UTC"));
            var sixtyDaysAgoString = DATE_FORMATTER.format(sixtyDaysAgo);
            softly.assertThat(certInfo.getIssuedOn()).startsWith(sixtyDaysAgoString);

            var expirationDate = sixtyDaysAgo.plusDays(75);
            var expirationDateString = DATE_FORMATTER.format(expirationDate);
            softly.assertThat(certInfo.getExpiresOn()).startsWith(expirationDateString);
        }

        @SuppressWarnings("unchecked")
        @Test
        void whenAnExceptionIsThrown(SoftAssertions softly, @TempDir Path tempDir) {
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
                    entry("expirationWarningThreshold", Duration.days(30)) // default expirationWarningThreshold
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).isEmpty();

            List<BasicCertInfo> expiredCerts = getBasicCertInfoList(details, "expiredCerts");
            softly.assertThat(expiredCerts).isEmpty();

            List<BasicCertInfo> expiringCerts = getBasicCertInfoList(details, "expiringCerts");
            softly.assertThat(expiringCerts).isEmpty();
        }
    }

    @Nested
    class ShouldReportHealthy {

        @SuppressWarnings("unchecked")
        @Test
        void whenThereAreOnlyValidCerts(SoftAssertions softly, @TempDir Path tempDir) {
            var dn = "CN=Valid Test,OU=Development,O=Project,L=There,ST=VA,C=US";
            var path = createTemporaryKeystore(tempDir, 600, dn);

            var config = KeystoreConfig.builder()
                    .name("Test Keystore Config")
                    .path(path)
                    .pass("unittest")
                    .type(KeyStoreType.PKCS12.name())
                    .expirationWarningThreshold(Duration.days(20))
                    .build();

            var healthCheck = new ExpiringKeystoreHealthCheck(config);
            var result = healthCheck.check();

            softly.assertThat(result.isHealthy()).isTrue();
            softly.assertThat(result.getMessage()).isEqualTo(path);
            softly.assertThat(result.getError()).isNull();

            var details = result.getDetails();
            softly.assertThat(details).contains(
                    entry("path", path),
                    entry("expirationWarningThreshold", Duration.days(20))
            );

            List<BasicCertInfo> validCerts = getBasicCertInfoList(details, "validCerts");
            softly.assertThat(validCerts).hasSize(1);

            var certInfo = first(validCerts);
            softly.assertThat(certInfo.getSubjectDN()).isEqualTo("CN=Valid Test, OU=Development, O=Project, L=There, ST=VA, C=US");
            softly.assertThat(certInfo.getIssuerDN()).isEqualTo("CN=Valid Test, OU=Development, O=Project, L=There, ST=VA, C=US");

            var sixtyDaysAgo = ZonedDateTime.now().minusDays(60).toInstant().atZone(ZoneId.of("UTC"));
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

    private String createTemporaryKeystore(Path dir, int daysValid, String dn) {
        var keystorePath = dir.resolve("temporary-keystore.jks").toAbsolutePath().toString();

        var processHelper = new ProcessHelper();
        var keystoreGenProcess = processHelper.launch("keytool",
                "-alias", "Test",
                "-genkey",
                "-keystore", keystorePath,
                "-keyalg", "RSA",
                "-validity", String.valueOf(daysValid),
                "-startdate", "-60d",
                "-storepass", "unittest",
                "-keypass", "unittest",
                "-dname", dn);

        processHelper.waitForExit(keystoreGenProcess, 5, TimeUnit.SECONDS)
                .ifPresentOrElse(
                        exitCode -> LOG.info("keytool exited with code {}", exitCode),
                        () -> LOG.warn("keytool did not exit within timeout"));

        // These are here so that if the keytool command fails we can see the output
        LOG.debug("keytool stdout: [{}]", KiwiIO.readInputStreamOf(keystoreGenProcess));
        LOG.debug("keytool stderr: [{}]", KiwiIO.readErrorStreamOf(keystoreGenProcess));

        return keystorePath;
    }
}
