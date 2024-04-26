package org.kiwiproject.dropwizard.util.bundle;

import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.collect.KiwiLists.first;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.util.server.DropwizardConnectors;
import org.kiwiproject.net.LocalPortChecker;
import org.kiwiproject.registry.model.Port;
import org.kiwiproject.registry.model.Port.PortType;

import java.util.List;
import java.util.Map;

@DisplayName("DynamicPortsBundle")
@ExtendWith(DropwizardExtensionsSupport.class)
class DynamicPortsBundleTest {

    static final DropwizardAppExtension<MyDynamicPortsConfig> APP =
            new DropwizardAppExtension<>(MyDynamicPortsApp.class);

    private static final int MAX_DYNAMIC_PORT_BOUND = 1 + MyDynamicPortsApp.MAX_DYNAMIC_PORT;

    @Test
    void shouldAssignPortsInSpecifiedRange() {
        var myApp = (MyDynamicPortsApp) APP.getApplication();

        Map<PortType, List<Port>> portsByType = DropwizardConnectors.getPorts(APP.getConfiguration())
                .stream()
                .collect(groupingBy(Port::getType));

        var applicationPorts = portsByType.get(Port.PortType.APPLICATION);
        assertThat(applicationPorts).hasSize(1);
        assertThat(first(applicationPorts).getNumber()).isEqualTo(APP.getLocalPort());

        var adminPorts = portsByType.get(Port.PortType.ADMIN);
        assertThat(adminPorts).hasSize(1);
        assertThat(first(adminPorts).getNumber()).isEqualTo(APP.getAdminPort());

        assertAll(
            () -> assertThat(APP.getLocalPort()).isBetween(MyDynamicPortsApp.MIN_DYNAMIC_PORT, MAX_DYNAMIC_PORT_BOUND),
            () -> assertThat(APP.getAdminPort()).isBetween(MyDynamicPortsApp.MIN_DYNAMIC_PORT, MAX_DYNAMIC_PORT_BOUND),
            () -> assertThat(myApp.getLocalPortChecker().portCheckCount).hasValue(2)
        );
    }

    @Test
    void shouldUseLocalPortChecker() {
        var dynamicPortsBundle = new DynamicPortsBundle<MyDynamicPortsConfig>() {
            @Override
            public DynamicPortsConfiguration getDynamicPortsConfiguration(MyDynamicPortsConfig configuration) {
                return new DynamicPortsConfiguration();
            }
        };

        assertThat(dynamicPortsBundle.getLocalPortChecker()).isExactlyInstanceOf(LocalPortChecker.class);
    }
}
