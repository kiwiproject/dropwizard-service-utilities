package org.kiwiproject.dropwizard.util.config;

import io.dropwizard.util.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.kiwiproject.security.KeyStoreType;

/**
 * Configuration for keystore health checks.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class KeystoreConfig {

    private static final String DEFAULT_NAME = "Key store";
    private static final String DEFAULT_TYPE = KeyStoreType.JKS.name();
    private static final Duration DEFAULT_EXPIRATION_WARNING_THRESHOLD = Duration.days(30);

    /**
     * A name that can be used for the health check using this configuration.
     */
    @NotBlank
    @Builder.Default
    private String name = DEFAULT_NAME;

    /**
     * The absolute path to the key store.
     */
    @NotBlank
    private String path;

    /**
     * The keystore password. (Default: "")
     */
    @Builder.Default
    @NotNull
    private String pass = "";

    /**
     * The key store type. (Default: JKS)
     */
    @NotBlank
    @Builder.Default
    private String type = DEFAULT_TYPE;

    /**
     * The threshold to use when considering whether a certificate will expire "soon". The definition of "soon"
     * is whether the certificate expires within the threshold of the certificate's expiration date. (Default: 30 days)
     * <p>
     * For example, with the default 30 days, if a certificate's expiration date is before (now + 30 days) then it will
     * be considered as expiring soon.
     */
    @NotNull
    @Builder.Default
    private Duration expirationWarningThreshold = DEFAULT_EXPIRATION_WARNING_THRESHOLD;
}
