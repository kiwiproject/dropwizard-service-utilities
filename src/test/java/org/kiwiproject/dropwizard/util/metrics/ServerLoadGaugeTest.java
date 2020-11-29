package org.kiwiproject.dropwizard.util.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServerLoadGauge")
class ServerLoadGaugeTest {

    @Test
    void shouldSet_NAME_Constant() {
        assertThat(ServerLoadGauge.NAME).isEqualTo("org.kiwiproject.dropwizard.util.metrics.ServerLoad.load-average");
    }

    @Test
    void shouldGetValue() {
        var gauge = new ServerLoadGauge();
        var value = gauge.getValue();
        assertThat(value)
                .isNotNull()
                .matches("^\\d+.\\d+[,]? \\d+.\\d+[,]? \\d+.\\d+$");
    }
}
