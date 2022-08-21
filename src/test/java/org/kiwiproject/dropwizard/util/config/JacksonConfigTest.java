package org.kiwiproject.dropwizard.util.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JacksonConfig")
class JacksonConfigTest {

    @Nested
    class NoArgsConstructor {

        @Test
        void shouldConstructWithFieldDefaultValues() {
            var config = new JacksonConfig();

            assertThat(config.isIgnoreButWarnForUnknownJsonProperties()).isTrue();
            assertThat(config.isRegisterHealthCheckForUnknownJsonProperties()).isTrue();
            assertThat(config.isReadAndWriteDateTimestampsAsMillis()).isTrue();
            assertThat(config.isWriteNilJaxbElementsAsNull()).isTrue();
        }
    }

    @Nested
    class Builder {

        @Test
        void shouldConstructWithDefaultValues() {
            var config = JacksonConfig.builder().build();

            assertThat(config.isIgnoreButWarnForUnknownJsonProperties()).isTrue();
            assertThat(config.isRegisterHealthCheckForUnknownJsonProperties()).isTrue();
            assertThat(config.isReadAndWriteDateTimestampsAsMillis()).isTrue();
            assertThat(config.isWriteNilJaxbElementsAsNull()).isTrue();
        }
    }
}
