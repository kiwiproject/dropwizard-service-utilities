package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;

@DisplayName("PortAssigners")
class PortAssignersTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
        true, DYNAMIC
        false, STATIC
        """)
    void shouldCreatePortAssignment(boolean useDynamicPorts,
                                    PortAssigner.PortAssignment expectedPortAssignment) {

        var dynamicPortsConfig = DynamicPortsConfiguration.builder()
                .useDynamicPorts(useDynamicPorts)
                .build();

        assertThat(PortAssigners.portAssignmentFrom(dynamicPortsConfig))
                .isEqualTo(expectedPortAssignment);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        5000, 6000
        15000, 16000,
        35000, 38000
        """)
    void shouldCreateAllowablePortRange(int minPort, int maxPort) {
        var dynamicPortsConfig = DynamicPortsConfiguration.builder()
                .minDynamicPort(minPort)
                .maxDynamicPort(maxPort)
                .build();

        var portRange = PortAssigners.allowablePortRangeFrom(dynamicPortsConfig);

        assertAll(
            () -> assertThat(portRange.getMinPortNumber()).isEqualTo(minPort),
            () -> assertThat(portRange.getMaxPortNumber()).isEqualTo(maxPort)
        );
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
        true, SECURE
        false, NON_SECURE
        """)
    void shouldCreatePortSecurity(boolean useSecureDynamicPorts,
                                  PortAssigner.PortSecurity expectedPortSecurity) {

        var dynamicPortsConfig = DynamicPortsConfiguration.builder()
                .useSecureDynamicPorts(useSecureDynamicPorts)
                .build();

        assertThat(PortAssigners.portSecurityFrom(dynamicPortsConfig))
                .isEqualTo(expectedPortSecurity);
    }
}
