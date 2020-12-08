package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.json.LoggingDeserializationProblemHandler;

import java.util.Set;

@DisplayName("UnknownPropertiesHealthCheck")
class UnknownPropertiesHealthCheckTest {

    private LoggingDeserializationProblemHandler handler;
    private UnknownPropertiesHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        handler = mock(LoggingDeserializationProblemHandler.class);
        healthCheck = new UnknownPropertiesHealthCheck(handler);
    }

    @Nested
    class IsHealthy {

        @Test
        void whenHandlerDoesNotHaveAnyUnknownProperties() {
            when(handler.getUnknownPropertyCount()).thenReturn(0L);

            assertThat(healthCheck)
                    .isHealthy()
                    .hasMessage("No unknown properties detected");
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenHandlerHasOneUnknownProperty() {
            when(handler.getUnknownPropertyCount()).thenReturn(1L);

            var paths = Set.of("badPath");
            when(handler.getUnexpectedPropertyPaths()).thenReturn(paths);

            assertThat(healthCheck)
                    .isUnhealthy()
                    .hasMessage("1 unknown property detected")
                    .hasDetail("unexpectedPaths", paths);
        }

        @Test
        void whenHandlerHasMultipleUnknownProperty() {
            when(handler.getUnknownPropertyCount()).thenReturn(2L);

            var paths = Set.of("badPath", "anotherBadPath");
            when(handler.getUnexpectedPropertyPaths()).thenReturn(paths);

            assertThat(healthCheck)
                    .isUnhealthy()
                    .hasMessage("2 unknown properties detected")
                    .hasDetail("unexpectedPaths", paths);
        }
    }
}
