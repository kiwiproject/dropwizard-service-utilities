package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@DisplayName("ProcessGrepHealthCheck")
class ProcessGrepHealthCheckTest {

    private ProcessGrepHealthCheck healthCheck;
    private ProcessHelper processes;

    @BeforeEach
    void setUp() {
        processes = mock(ProcessHelper.class);
        healthCheck = new ProcessGrepHealthCheck(processes);
    }

    @Nested
    class IsHealthy {

        @Test
        void whenJavaProcessesFound() {
            when(processes.pgrep("java")).thenReturn(List.of(1111L, 2222L));
            assertThatHealthCheck(healthCheck).isHealthy();
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenNoJavaProcessesFound() {
            when(processes.pgrep("java")).thenReturn(List.of());

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage("No java processes found (even though this app is a java processes)");
        }

        @Test
        void whenExceptionIsThrown() {
            var message = "pgrep error";

            var ioException = new IOException(message);
            var uncheckedIOException = new UncheckedIOException(ioException);

            when(processes.pgrep("java")).thenThrow(uncheckedIOException);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessageStartingWith("Failed to find java processes: ")
                    .hasMessageEndingWith(uncheckedIOException.getMessage());
        }
    }
}
