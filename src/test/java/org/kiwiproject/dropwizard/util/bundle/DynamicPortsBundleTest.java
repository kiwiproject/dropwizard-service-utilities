package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.dropwizard.util.bundle.DynamicPortsTestHelpers.assertExpectedAdminPort;
import static org.kiwiproject.dropwizard.util.bundle.DynamicPortsTestHelpers.assertExpectedApplicationPort;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("DynamicPortsBundle")
@ExtendWith(DropwizardExtensionsSupport.class)
class DynamicPortsBundleTest {

    static final DropwizardAppExtension<MyDynamicPortsConfig> APP =
            new DropwizardAppExtension<>(MyDynamicPortsApp.class);

    @Test
    void shouldAssignPortsInSpecifiedRange() {
        assertExpectedApplicationPort(APP);
        assertExpectedAdminPort(APP);

        assertAll(
            () -> assertThat(APP.getLocalPort()).isBetween(MyDynamicPortsApp.MIN_DYNAMIC_PORT, MyDynamicPortsApp.MAX_DYNAMIC_PORT),
            () -> assertThat(APP.getAdminPort()).isBetween(MyDynamicPortsApp.MIN_DYNAMIC_PORT, MyDynamicPortsApp.MAX_DYNAMIC_PORT)
        );
    }
}
