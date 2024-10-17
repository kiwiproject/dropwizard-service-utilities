package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("DynamicPortsBundle (when using static ports)")
@ExtendWith(DropwizardExtensionsSupport.class)
class DynamicPortsBundleForStaticPortsTest {

    static final DropwizardAppExtension<MyDynamicPortsConfig> APP = new DropwizardAppExtension<>(
        MyDynamicPortsApp.class,
        new MyDynamicPortsConfig().withUseDynamicPorts(false));

    private static final int DEFAULT_DROPWIZARD_APPLICATION_PORT = 8080;
    private static final int DEFAULT_DROPWIZARD_ADMIN_PORT = 8081;

    @Test
    void shouldUseStaticPorts() {
        assertAll(
            () -> assertThat(APP.getLocalPort()).isEqualTo(DEFAULT_DROPWIZARD_APPLICATION_PORT),
            () -> assertThat(APP.getAdminPort()).isEqualTo(DEFAULT_DROPWIZARD_ADMIN_PORT)
        );
    }
}
