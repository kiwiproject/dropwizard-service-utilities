package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.dropwizard.util.health.ProcessLaunchHealthCheck.ECHO_MESSAGE;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@DisplayName("ProcessLaunchHealthCheck")
class ProcessLaunchHealthCheckTest {

    private ProcessLaunchHealthCheck healthCheck;
    private ProcessHelper processes;
    private Process process;

    @BeforeEach
    void setup() {
        process = mock(Process.class);
        processes = mock(ProcessHelper.class);
        healthCheck = new ProcessLaunchHealthCheck(processes);
    }

    @Nested
    class IsHealthy {

        @Test
        void whenEchoProcessCanBeLaunched() {
            when(processes.launch("echo", ECHO_MESSAGE)).thenReturn(process);

            var inputStream = toInputStream(ECHO_MESSAGE);
            when(process.getInputStream()).thenReturn(inputStream);

            assertThatHealthCheck(healthCheck)
                    .isHealthy();
        }

        /**
         * Only running on Linux and macOS since we know for a fact that 'echo' exists there.
         */
        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void whenEchoProcessCanBeLaunched_UsingActualProcess() {
            healthCheck = new ProcessLaunchHealthCheck(new ProcessHelper());

            assertThatHealthCheck(healthCheck)
                    .isHealthy();
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenEchoMessageDoesNotMatchExpectedOutput() {
            when(processes.launch("echo", ECHO_MESSAGE)).thenReturn(process);

            var droidsMessage = "These are not the droids you're looking for.. ";
            var inputStream = toInputStream(droidsMessage);
            when(process.getInputStream()).thenReturn(inputStream);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(f("Output [{}] from 'echo' does not match expected [{}]",
                            droidsMessage, ECHO_MESSAGE));
        }

        @Test
        void whenExceptionIsThrown() {
            var message = "Process launch failure";
            IOException ioException = new IOException(message);
            UncheckedIOException uncheckedIOException = new UncheckedIOException(ioException);

            when(processes.launch("echo", ECHO_MESSAGE)).thenThrow(uncheckedIOException);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessageStartingWith("Failed launching an 'echo' process: ")
                    .hasMessageEndingWith(uncheckedIOException.getMessage());
        }
    }

    private InputStream toInputStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}

