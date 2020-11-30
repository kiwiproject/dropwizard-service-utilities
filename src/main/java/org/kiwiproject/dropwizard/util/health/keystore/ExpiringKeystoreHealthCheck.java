package org.kiwiproject.dropwizard.util.health.keystore;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static org.kiwiproject.base.KiwiStrings.format;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.util.config.KeystoreConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * Health check that checks whether X.509 certificates are valid, expired, or will be expiring soon as configured
 * in a {@link KeystoreConfig}.
 */
@Slf4j
public class ExpiringKeystoreHealthCheck extends HealthCheck {

    /**
     * @implNote There is no constant in JDK for this, however, see 'Java Security Standard Algorithm Names' on
     * the interwebs, specifically the 'CertificateFactoryTypes' subsection which defines X.509 as (currently the only)
     * a valid type. Note each JDK version has its own 'Java Security Standard Algorithm Names' page.
     * @see java.security.cert.CertificateFactory
     */
    private static final String X_509_CERT_TYPE = "X.509";

    /**
     * Indicates the stream should not be split in parallel.
     */
    private static final boolean USE_SEQUENTIAL_STREAM = false;

    private final KeystoreConfig keystoreConfig;

    public ExpiringKeystoreHealthCheck(KeystoreConfig keystoreConfig) {
        this.keystoreConfig = keystoreConfig;
    }

    @Override
    protected Result check() throws Exception {
        var keystoreResults = checkKeyStore();

        return keystoreResults.toResultBuilder()
                .withDetail("path", keystoreResults.getPath())
                .withDetail("ttl", keystoreConfig.getTtl())
                .withDetail("validCerts", keystoreResults.getValidCerts())
                .withDetail("expiredCerts", keystoreResults.getExpiredCerts())
                .withDetail("expiringCerts", keystoreResults.getExpiringCerts())
                .build();
    }

    private KeystoreHealthResults checkKeyStore() {
        try (var inputStream = new FileInputStream(keystoreConfig.getPath())) {
            return checkKeyStore(inputStream);
        } catch (Exception e) {
            LOG.error("Error checking keystore: {}", keystoreConfig.getPath(), e);

            return KeystoreHealthResults.builder()
                    .path(keystoreConfig.getPath())
                    .ttl(keystoreConfig.getTtl())
                    .exception(e)
                    .validCerts(emptyList())
                    .expiredCerts(emptyList())
                    .expiringCerts(emptyList())
                    .build();
        }
    }

    private KeystoreHealthResults checkKeyStore(FileInputStream inputStream)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {

        var keyStore = KeyStore.getInstance(keystoreConfig.getType());
        keyStore.load(inputStream, Optional.ofNullable(keystoreConfig.getPass()).orElse("").toCharArray());

        return checkKeyStore(keyStore);
    }

    private KeystoreHealthResults checkKeyStore(KeyStore keyStore) throws KeyStoreException {
        var now = Instant.now();
        var expirationThreshold = now.plusSeconds(keystoreConfig.getTtl().toSeconds());

        var basicCertInfos = StreamSupport
                .stream(keyStoreAliasSpliterator(keyStore), USE_SEQUENTIAL_STREAM)
                .map(alias -> toCertificate(alias, keystoreConfig.getPath(), keyStore))
                .filter(ExpiringKeystoreHealthCheck::isX509)
                .map(X509Certificate.class::cast)
                .map(x509Cert -> BasicCertInfo.from(x509Cert, now, expirationThreshold))
                .collect(groupingBy(BasicCertInfo::getCertStatus));

        var ok = basicCertInfos.getOrDefault(CertStatus.VALID, emptyList());
        var expired = basicCertInfos.getOrDefault(CertStatus.EXPIRED, emptyList());
        var expiring = basicCertInfos.getOrDefault(CertStatus.EXPIRING_SOON, emptyList());

        return KeystoreHealthResults.builder()
                .path(keystoreConfig.getPath())
                .ttl(keystoreConfig.getTtl())
                .validCerts(ok)
                .expiredCerts(expired)
                .expiringCerts(expiring)
                .build();
    }

    private static Spliterator<String> keyStoreAliasSpliterator(KeyStore keyStore) throws KeyStoreException {
        return Spliterators.spliteratorUnknownSize(keyStore.aliases().asIterator(), Spliterator.ORDERED);
    }

    @VisibleForTesting
    static Certificate toCertificate(String alias, String path, KeyStore keyStore) {
        try {
            var certificateOrNull = keyStore.getCertificate(alias);

            return requireNonNull(certificateOrNull, () -> format("Certificate for alias {} was null", alias));
        } catch (KeyStoreException e) {
            throw new KeyStoreNotLoadedException(path, e);
        }
    }

    static class KeyStoreNotLoadedException extends RuntimeException {
        KeyStoreNotLoadedException(String path, Throwable cause) {
            super(format("Keystore at path {} not loaded", path), cause);
        }
    }

    private static boolean isX509(Certificate cert) {
        return cert.getType().equals(X_509_CERT_TYPE);
    }
}
