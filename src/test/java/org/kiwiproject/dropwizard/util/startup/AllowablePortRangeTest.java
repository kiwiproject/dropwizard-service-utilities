package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PortRangeInfo")
class AllowablePortRangeTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65_536})
    void shouldThrowIllegalStateException_WhenMinPortIsNotValid(int minPort) {
        assertThatThrownBy(() -> new AllowablePortRange(minPort, minPort + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("minPort must be between 1 and 65534 (was: %d)", minPort);
    }

    @ParameterizedTest
    @ValueSource(ints = {65_536, 100_000, 1_000_000})
    void shouldThrowIllegalStateException_WhenMaxPortIsNotValid(int maxPort) {
        assertThatThrownBy(() -> new AllowablePortRange(9_000, maxPort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("maxPort must be between 2 and 65535 (was: %d)", maxPort);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "8000, 8000",
            "8081, 8080",
            "9000, 8000",
            "65535, 65535"
    })
    void shouldThrowIllegalStateException_WhenMaxPortIsNotGreaterThanMaxPort(int minPort, int maxPort) {
        assertThatThrownBy(() -> new AllowablePortRange(minPort, maxPort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("minPortNumber must be less than maxPortNumber (was: %d -> %d)", minPort, maxPort);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 2",
            "1, 65535",
            "8000, 9000",
            "9050, 9100",
            "45000, 46000",
            "64534, 65535",
    })
    void shouldCreateNewPortRangeInfo_WhenValidPortsGiven(int minPort, int maxPort) {
        var info = new AllowablePortRange(minPort, maxPort);

        assertThat(info.getMinPortNumber()).isEqualTo(minPort);
        assertThat(info.getMaxPortNumber()).isEqualTo(maxPort);
        var expectedPortCount = 1 + (maxPort - minPort);
        assertThat(info.getNumPortsInRange()).isEqualTo(expectedPortCount);
        assertThat(info.getMaxPortCheckAttempts()).isEqualTo(3 * expectedPortCount);
    }
}
