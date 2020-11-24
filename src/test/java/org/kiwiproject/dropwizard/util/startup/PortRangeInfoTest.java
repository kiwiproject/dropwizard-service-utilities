package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PortRangeInfo")
class PortRangeInfoTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, 65_536})
    void shouldThrowIllegalStateException_WhenMinPortIsNotValid(int port) {
        assertThatThrownBy(() -> new PortRangeInfo(port, port + 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("port must be between 0 and 65535");
    }

    @Test
    void shouldThrowIllegalStateException_WhenMaxPortIsNotValid() {
        assertThatThrownBy(() -> new PortRangeInfo(9_000, 65_536))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("port must be between 0 and 65535");
    }

    @Test
    void shouldThrowIllegalStateException_WhenMinPortIsGreaterThanMaxPort() {
        assertThatThrownBy(() -> new PortRangeInfo(9_000, 8_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("minPortNumber must be less than maxPortNumber");
    }

    @Test
    void shouldCreateNewPortRangeInfo_WhenValidPortsGiven() {
        var info = new PortRangeInfo(8_000, 9_000);

        assertThat(info.getMinPortNumber()).isEqualTo(8_000);
        assertThat(info.getMaxPortNumber()).isEqualTo(9_000);
        assertThat(info.getNumPortsInRange()).isEqualTo(1_001);
        assertThat(info.getMaxPortCheckAttempts()).isEqualTo(3_003);
    }
}
