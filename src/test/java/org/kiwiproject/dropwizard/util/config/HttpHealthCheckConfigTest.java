package org.kiwiproject.dropwizard.util.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.util.health.HttpConnectionsHealthCheck;

@DisplayName("HttpHealthCheckConfig")
class HttpHealthCheckConfigTest {

    @Test
    void shouldSetDefaultName() {
        var config = HttpHealthCheckConfig.builder().build();
        assertThat(config.getName()).isEqualTo(HttpConnectionsHealthCheck.DEFAULT_NAME);
    }

    @Test
    void shouldSetDefaultWarningThreshold_WhenNoValueSupplied() {
        var config = HttpHealthCheckConfig.builder().build();
        assertThat(config.getWarningThreshold()).isEqualTo(HttpConnectionsHealthCheck.DEFAULT_WARNING_THRESHOLD);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-50.0, -1.0, 0.0})
    void shouldSetDefaultWarningThreshold(double value) {
        var config = HttpHealthCheckConfig.builder().warningThreshold(value).build();
        assertThat(config.getWarningThreshold()).isEqualTo(HttpConnectionsHealthCheck.DEFAULT_WARNING_THRESHOLD);
    }

}
