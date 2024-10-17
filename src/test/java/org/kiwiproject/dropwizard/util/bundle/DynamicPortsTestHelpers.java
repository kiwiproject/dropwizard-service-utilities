package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;

import io.dropwizard.core.Configuration;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.experimental.UtilityClass;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors;

@UtilityClass
class DynamicPortsTestHelpers {

    static <C extends Configuration> void assertExpectedApplicationPort(DropwizardAppExtension<C> app) {
        var applicationPorts = DropwizardConnectors.getApplicationPorts(app.getConfiguration());
        assertThat(applicationPorts).hasSize(1);
        assertThat(first(applicationPorts).getNumber()).isEqualTo(app.getLocalPort());
    }

    static <C extends Configuration> void assertExpectedAdminPort(DropwizardAppExtension<C> app) {
        var adminPorts = DropwizardConnectors.getAdminPorts(app.getConfiguration());
        assertThat(adminPorts).hasSize(1);
        assertThat(first(adminPorts).getNumber()).isEqualTo(app.getAdminPort());
    }
}
